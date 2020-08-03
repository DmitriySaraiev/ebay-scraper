package com.saraiev.ebayscraper.scraper;

import com.saraiev.ebayscraper.domain.Ad;
import com.saraiev.ebayscraper.domain.Category;
import com.saraiev.ebayscraper.domain.DocumentIntegerWrapper;
import com.saraiev.ebayscraper.repository.AdCategoryRepository;
import com.saraiev.ebayscraper.repository.AdRepository;
import com.saraiev.ebayscraper.repository.CategoryRepository;
import com.saraiev.ebayscraper.tools.Downloader;
import lombok.SneakyThrows;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EbayScraper implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EbayScraper.class);

    private final Downloader downloader;

    private final AdRepository adRepository;

    private final CategoryRepository categoryRepository;

    private final AdCategoryRepository adCategoryRepository;

    private final int threads = 5;

    public EbayScraper(Downloader downloader, AdRepository adRepository, CategoryRepository categoryRepository, AdCategoryRepository adCategoryRepository) {
        this.downloader = downloader;
        this.adRepository = adRepository;
        this.categoryRepository = categoryRepository;
        this.adCategoryRepository = adCategoryRepository;
    }

    @SneakyThrows
    private void scrape(String sellerUrl) {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<Future> futures = new ArrayList<>();
        List<String> urls = scrapeAllUrls(sellerUrl, executorService);

        urls = urls.subList(29200, urls.size());

        AtomicInteger counter = new AtomicInteger();
        for (String url : urls) {
            futures.add(executorService.submit(() -> {
                parseUrl(url, counter.incrementAndGet());
            }));
        }
        for (Future future : futures) {
            future.get();
        }
    }

    @SneakyThrows
    private List<String> scrapeAllUrls(String sellerUrl, ExecutorService executorService) {
        Double highestPrice = findHighestPrice(sellerUrl);
        Integer min = 0;
        Integer max = 1;
        ArrayList<String> urls = new ArrayList<>();
        while (min < highestPrice) {
            DocumentIntegerWrapper documentIntegerWrapper = findNextPricePage(sellerUrl, min, max, highestPrice);

            Document document = documentIntegerWrapper.getDocument();
            max = documentIntegerWrapper.getMax();

            String source;
            Element elemCountEl = document.selectFirst("span.rcnt");
            Integer elemCount = Integer.parseInt(elemCountEl.text().replaceAll("\\.", ""));
            int pages;
            if (elemCount % 50 == 0) {
                pages = elemCount / 50;
            } else {
                pages = (elemCount / 50) + 1;
            }

            List<Future> futures = new ArrayList<>();
            for (int i = 1; i < pages + 1; i++) {
                int finalI = i;
                Integer finalMin = min;
                Integer finalMax = max;
                futures.add(executorService.submit(() -> {
                    try {
                        String pageUrl = sellerUrl + String.format("&_udlo=%s&_udhi=%s&_pgn=%s", finalMin, finalMax, finalI);
                        String sourceInner = downloader.get(pageUrl);
                        Document documentInner = Jsoup.parse(sourceInner);
                        Elements urlElems = documentInner.select("h3.lvtitle a");
                        for (Element urlElem : urlElems) {
                            urls.add(urlElem.attr("href"));
                        }
                        logger.info("page {} found {} items {}", finalI, urlElems.size(), pageUrl);
                    } catch (Exception e) {
                        logger.error("errer", e);
                    }
                }));
            }
            for (Future future : futures) {
                future.get();
            }
            logger.info("found {} urls in diapason {}-{}", urls.size(), min, max);
            min = documentIntegerWrapper.getMax();
            max = min + 1;
        }
        logger.info("found {} urls", urls.size());
        return urls;
    }

    @SneakyThrows
    private Double findHighestPrice(String selelruUrl) {
        String url = selelruUrl + "&_sop=3";
        String source = downloader.get(url);
        Document document = Jsoup.parse(source);
        Element firstAd = document.selectFirst("li.lvprice.prc");
        String priceStr = firstAd.text().replaceAll("\\.", "").replaceAll(",", ".").replaceAll("[^.0-9]", "");
        return Double.parseDouble(priceStr);
    }

    @SneakyThrows
    private DocumentIntegerWrapper findNextPricePage(String sellerUrl, Integer min, Integer max, Double highestPrice) {
        DocumentIntegerWrapper documentIntegerWrapper = new DocumentIntegerWrapper();
        String url = sellerUrl + String.format("&_udlo=%s&_udhi=%s", min, max);
        int multiplier = 1;
        int prevMax = max;
        String source = downloader.get(url);
        Document document = Jsoup.parse(source);
        documentIntegerWrapper.setDocument(document);
        documentIntegerWrapper.setMax(max);
        Element countEl = document.selectFirst("span.rcnt");
        Integer count = Integer.parseInt(countEl.text().replaceAll("\\.", ""));
        while (true) {
            try {
                logger.info("price diapason {} - {} {} items", min, max, count);
                if (max > highestPrice) {
                    documentIntegerWrapper.setMax(highestPrice.intValue() + 1);
                    return documentIntegerWrapper;
                }
                if (count < 10000) {
                    documentIntegerWrapper.setDocument(document);
                    prevMax = max;
                    max += multiplier++;
                    url = sellerUrl + String.format("&_udlo=%s&_udhi=%s", min, max);
                    source = downloader.get(url);
                    document = Jsoup.parse(source);
                    countEl = document.selectFirst("span.rcnt");
                    count = Integer.parseInt(countEl.text().replaceAll("\\.", ""));
                } else {
                    documentIntegerWrapper.setMax(prevMax);
                    return documentIntegerWrapper;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseUrl(String url, int counter) {
        try {
            Ad adSavedPreviosly = adRepository.findByUrl(url);
            if (adSavedPreviosly != null) {
                logger.info("{} duplicate {}", counter, url);
                return;
            }
            String source = downloader.get(url);
            Document document = Jsoup.parse(source);
            Element nameEl = document.selectFirst("h1.it-ttl");
            String name = nameEl.ownText();
            Element priceEl = document.selectFirst("span[itemprop=price]");
            String priceStr = priceEl.text();
            Double price = Double.parseDouble(priceStr.replaceAll("[^,0-9]", "").replaceAll(",", "."));

            String categoryLabel = null;
            String categoryValue = null;

//            Element sellerDescElem = document.selectFirst("table#itmSellerDesc");
//            if (sellerDescElem != null) {
//                Elements categorySellerLabelElems = sellerDescElem.select("th");
//                for (Element categorySellerLabelElem : categorySellerLabelElems) {
//                    categoryLabel = categorySellerLabelElem.text().replaceAll(":", "");
//                    categoryValue = categorySellerLabelElem.nextElementSibling().text();
////                    logger.info("{} {}", categoryLabel, categoryValue);
//                }
//            }

            Ad ad = new Ad();
            ad.setName(name);
            ad.setPrice(price);
            ad.setUrl(url);

//            Ad savedAd = adRepository.save(ad);

            Elements categoryLabelElems = document.select("table[role=presentation] td.attrLabels");
            Elements categoryValueElems = document.select("table[role=presentation] td[width]");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < categoryLabelElems.size(); i++) {
                Element categoryLabelElem = categoryLabelElems.get(i);
                Element categoryValueElem = categoryValueElems.get(i);
                categoryLabel = categoryLabelElem.text().replaceAll(":", "");
                categoryValue = categoryValueElem.text();

                Element categoryValueMoreEl = categoryValueElem.selectFirst("span#hiddenContent");
                if (categoryValueMoreEl != null) {
                    Element aScriptEl = categoryValueMoreEl.selectFirst("a");
                    categoryValue = categoryValue.replaceAll(aScriptEl.text() + ".*", "");
                }
                Category byName = categoryRepository.findByName(categoryLabel);
                Category savedCategory;
                if (byName == null) {
                    Category newCategory = new Category();
                    newCategory.setName(categoryLabel);
                    savedCategory = categoryRepository.save(newCategory);
                } else {
                    savedCategory = byName;
                }
                sb.append(categoryLabel).append(":::").append(categoryValue).append(",,,");
//                AdCategory adCategory = new AdCategory();
//                adCategory.setAdId(savedAd.getId());
//                adCategory.setCategoryId(savedCategory.getId());
//                adCategory.setValue(categoryValue);
//                adCategoryRepository.save(adCategory);
//                logger.info("{} {}", categoryLabel, categoryValue);
            }
            ad.setCategoryValue(sb.toString());
            adRepository.save(ad);
            logger.info("{} {} {} {}", counter, url, name, price);
        } catch (Exception e) {
            logger.error("error {}", url, e);
        }
    }

    private void generateExcell(String file) {
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            Workbook book = new XSSFWorkbook();
            Sheet sheet = book.createSheet("ebay");
            AtomicInteger rowCounter = new AtomicInteger();
            AtomicInteger cellCounter = new AtomicInteger();
            Row row = sheet.createRow(rowCounter.getAndIncrement());
            Cell cell = row.createCell(cellCounter.getAndIncrement());
            cell.setCellValue("Url");
            cell = row.createCell(cellCounter.getAndIncrement());
            cell.setCellValue("Name");
            cell = row.createCell(cellCounter.getAndIncrement());
            cell.setCellValue("Price");
            List<String> categoryNames = new ArrayList<>();
            List<Category> categories = categoryRepository.findAll();
            for (Category category : categories) {
                cell = row.createCell(cellCounter.getAndIncrement());
                cell.setCellValue(category.getName());
                categoryNames.add(category.getName());
            }
            List<Ad> ads = adRepository.findAll();
            List<Future> futures = new ArrayList<>();
            for (Ad ad : ads) {
                futures.add(executorService.submit(() -> {
                    Row row1 = sheet.createRow(rowCounter.getAndIncrement());
                    cellCounter.set(0);
                    Cell cell1 = row1.createCell(cellCounter.getAndIncrement());
                    cell1.setCellValue(ad.getUrl());
                    cell1 = row1.createCell(cellCounter.getAndIncrement());
                    cell1.setCellValue(ad.getName());
                    cell1 = row1.createCell(cellCounter.getAndIncrement());
                    cell1.setCellValue(ad.getPrice());
                    List<String[]> tokenizedCategories = tokenizeCategories(ad.getCategoryValue());
                    for (String[] tokenizedCategory : tokenizedCategories) {
                        int cellIndex = categoryNames.indexOf(tokenizedCategory[0]);
                        if (cellIndex != -1) {
                            cell1 = row1.createCell(cellIndex + 3);
                            cell1.setCellValue(tokenizedCategory[1]);
                        }
                    }
                    logger.info("created row {}/{}", rowCounter, ads.size());
                }));
            }
            for (Future future : futures) {
                future.get();
            }
            book.write(new FileOutputStream(file));
            book.close();
            logger.info("finished generating xls");
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    private List<String[]> tokenizeCategories(String categories) {
        ArrayList<String[]> tokenizedCategories = new ArrayList<>();
        String[] splittedCategories = categories.split(",,,");
        for (String splittedCategory : splittedCategories) {
            String[] split = splittedCategory.split(":::");
            tokenizedCategories.add(split);
        }
        return tokenizedCategories;
    }

    @Override
    public void run(String... args) throws Exception {
        scrape("https://www.ebay.de/sch/Auto-Ersatz-Reparaturteile/9884/m.html?_nkw&_armrs=1&_ipg&_from&_ssn=canis-lupus.digital&_clu=2&_fcid=77&_localstpos=56457&_stpos=56457&gbr=1");
//        generateExcell("data.xlsx");
//        parseUrl("https://www.ebay.de/itm/200x-HELLA-4RA-007-791-017-Relais-Arbeitsstrom-12V-mit-Widerstand/192819170899?hash=item2ce4eb0e53:g:71sAAOSwNZRfDkg9", 1);
    }

}
