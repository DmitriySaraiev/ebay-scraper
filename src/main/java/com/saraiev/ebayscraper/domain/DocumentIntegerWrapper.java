package com.saraiev.ebayscraper.domain;

import lombok.Data;
import org.jsoup.nodes.Document;

@Data
public class DocumentIntegerWrapper {

    private Document document;

    private Integer max;

}
