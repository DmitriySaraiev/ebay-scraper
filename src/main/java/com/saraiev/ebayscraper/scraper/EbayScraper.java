package com.saraiev.ebayscraper.scraper;

import com.saraiev.ebayscraper.domain.Ad;
import com.saraiev.ebayscraper.domain.AdCategory;
import com.saraiev.ebayscraper.domain.Category;
import com.saraiev.ebayscraper.repository.AdCategoryRepository;
import com.saraiev.ebayscraper.repository.AdRepository;
import com.saraiev.ebayscraper.repository.CategoryRepository;
import com.saraiev.ebayscraper.tools.Downloader;
import lombok.SneakyThrows;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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

    public EbayScraper(Downloader downloader, AdRepository adRepository, CategoryRepository categoryRepository, AdCategoryRepository adCategoryRepository) {
        this.downloader = downloader;
        this.adRepository = adRepository;
        this.categoryRepository = categoryRepository;
        this.adCategoryRepository = adCategoryRepository;
    }

    @SneakyThrows
    private void scrape(String sellerUrl) {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        List<Future> futures = new ArrayList<>();
        List<String> urls = scrapeAllUrls(sellerUrl);
        AtomicInteger counter = new AtomicInteger();
        for (String url : urls) {
            futures.add(executorService.submit(() -> {parseUrl(url, counter.incrementAndGet());}));
        }
        for (Future future : futures) {
            future.get();
        }
    }

    @SneakyThrows
    private List<String> scrapeAllUrls(String sellerUrl) {
        ArrayList<String> urls = new ArrayList<>();
        String source = downloader.get(sellerUrl);
        Document document = Jsoup.parse(source);
        Element elemCountEl = document.selectFirst("span.rcnt");
        Integer elemCount = Integer.parseInt(elemCountEl.text());
        int pages;
        if(elemCount % 50 == 0) {
            pages = elemCount / 50;
        } else {
            pages = (elemCount / 50) + 1;
        }
        for (int i = 1; i < pages + 1; i++) {
            String pageUrl = sellerUrl + String.format("&_pgn=%s", i);
            source = downloader.get(pageUrl);
            document = Jsoup.parse(source);
            Elements urlElems = document.select("h3.lvtitle a");
            for (Element urlElem : urlElems) {
                urls.add(urlElem.attr("href"));
            }
            logger.info("page {} found {} items", i, urlElems.size());
        }
        return urls;
    }

    private void parseUrl(String url, int counter ) {
        try {
            String source = downloader.get(url);
            Document document = Jsoup.parse(source);
            Element nameEl = document.selectFirst("h1.it-ttl");
            String name = nameEl.ownText();
            Element priceEl = document.selectFirst("span[itemprop=price]");
            String priceStr = priceEl.text();
            Double price = Double.parseDouble(priceStr.replaceAll("[^.0-9]", ""));

            String categoryLabel = null;
            String categoryValue = null;

            Element sellerDescElem = document.selectFirst("table#itmSellerDesc");
            if (sellerDescElem != null) {
                Elements categorySellerLabelElems = sellerDescElem.select("th");
                for (Element categorySellerLabelElem : categorySellerLabelElems) {
                    categoryLabel = categorySellerLabelElem.text().replaceAll(":", "");
                    categoryValue = categorySellerLabelElem.nextElementSibling().text();
                    logger.info("{} {}", categoryLabel, categoryValue);
                }
            }

            Ad ad = new Ad();
            ad.setName(name);
            ad.setPrice(price);
            ad.setUrl(url);

            Ad savedAd = adRepository.save(ad);

            Elements categoryLabelElems = document.select("table[role=presentation] td.attrLabels");
            Elements categoryValueElems = document.select("table[role=presentation] td[width]");
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
                AdCategory adCategory = new AdCategory();
                adCategory.setAdId(savedAd.getId());
                adCategory.setCategoryId(savedCategory.getId());
                adCategory.setValue(categoryValue);
                adCategoryRepository.save(adCategory);
                logger.info("{} {}", categoryLabel, categoryValue);
            }
            logger.info("{} {} {} {}\n\n", counter, url, name, price);
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    private void generateExcell(String file) {
        try {
            Workbook book = new HSSFWorkbook();
            Sheet sheet = book.createSheet("ebay");
            int rowCounter = 0;
            int cellCounter = 0;
            Row row = sheet.createRow(rowCounter++);
            Cell cell = row.createCell(cellCounter++);
            cell.setCellValue("Url");
            cell = row.createCell(cellCounter++);
            cell.setCellValue("Name");
            cell = row.createCell(cellCounter++);
            cell.setCellValue("Price");
            List<Category> categories = categoryRepository.findAll();
            for (Category category : categories) {
                cell = row.createCell(cellCounter++);
                cell.setCellValue(category.getName());
            }
            List<Ad> ads = adRepository.findAll();
            for (Ad ad : ads) {
                row = sheet.createRow(rowCounter++);
                cellCounter = 0;
                cell = row.createCell(cellCounter++);
                cell.setCellValue(ad.getUrl());
                cell = row.createCell(cellCounter++);
                cell.setCellValue(ad.getName());
                cell = row.createCell(cellCounter++);
                cell.setCellValue(ad.getPrice());
                for (Category category : categories) {
                    AdCategory adCategory = adCategoryRepository.findByAdIdAndCategoryId(ad.getId(), category.getId());
                    cell = row.createCell(cellCounter++);
                    if(adCategory != null) {
                        cell.setCellValue(adCategory.getValue());
                    } else {
                        cell.setBlank();
                    }
                }
                logger.info("created row {}/{}", rowCounter, ads.size());
            }
            book.write(new FileOutputStream(file));
            book.close();
            logger.info("finished generating xls");
        } catch (Exception e) {
            logger.error("error", e);

        }
    }

    @Override
    public void run(String... args) throws Exception {
//        scrape("https://www.ebay.co.uk/sch/Car-Parts/6030/m.html?_nkw&_armrs=1&_ipg&_from&_ssn=screwnutts&_dcat=6030&rt=nc&LH_ItemCondition=1000%7C1500%7C2500&_clu=2&_fcid=3&_localstpos=E10%207QZ&_stpos=E10%207QZ&gbr=1");
//        parseUrl("https://www.ebay.co.uk/itm/OPEL-VAUXHALL-C1-4NZ-LATE-CORSA-ASTRA-CAMSHAFT-ONLY/392295122325?hash=item5b569c8595:g:YDwAAOSwgDpc1tsu", 1);
        generateExcell("data.xls");
    }

}
