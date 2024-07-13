package org.example;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        WebCrawler crawler = new WebCrawler("E:\\dataMAIN");
        crawler.crawl("https://ru.wikipedia.org/wiki/Murda_Killa");
        System.out.println("WebCrawler finished.");
    }
}