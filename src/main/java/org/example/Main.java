package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Hello world!");

        WebCrawler crawler = new WebCrawler("saved_pages");

        // Запускаем сканирование, начиная с указанного URL
        crawler.crawl("https://ru.wikipedia.org/wiki/Murda_Killa");
    }
}