package com.saraiev.ebayscraper.scraper;

import com.saraiev.ebayscraper.repository.AdCategoryRepository;
import com.saraiev.ebayscraper.repository.AdRepository;
import com.saraiev.ebayscraper.repository.CategoryRepository;
import com.saraiev.ebayscraper.tools.Downloader;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.List;

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

    private void scrape(String sellerUrl) {
        List<String> urls = scrapeAllUrls(sellerUrl);
        int counter = 0;
        for (String url : urls) {
            parseUrl(url, ++counter);
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

    @SneakyThrows
    private void parseUrl(String url, int counter ) {
        String source = downloader.get(url);
        Document document = Jsoup.parse(source);
        Element nameEl = document.selectFirst("h1.it-ttl");
        String name = nameEl.ownText();
        Element priceEl = document.selectFirst("span[itemprop=price]");
        String priceStr = priceEl.text();
        Double price = Double.parseDouble(priceStr.replaceAll("[^.0-9]", ""));
        Elements categoryElems = document.select("table[role=presentation] td");
        for (Element categoryElem : categoryElems) {

        }
        logger.info("{} {} {} {}", counter, url, name, price);

    }

    @Override
    public void run(String... args) throws Exception {
        scrape("https://www.ebay.co.uk/sch/Car-Parts/6030/m.html?_nkw&_armrs=1&_ipg&_from&_ssn=screwnutts&_dcat=6030&rt=nc&LH_ItemCondition=1000%7C1500%7C2500&_clu=2&_fcid=3&_localstpos=E10%207QZ&_stpos=E10%207QZ&gbr=1");
    }
}