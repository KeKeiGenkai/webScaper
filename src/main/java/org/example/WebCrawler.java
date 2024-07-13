package org.example;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class WebCrawler {
    private static final String DB_URL = "jdbc:sqlite:links.db";
    private Set<String> visitedLinks = new HashSet<>();
    private Queue<String> linksToVisit = new LinkedList<>();
    private String baseDir;
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Logger logger = LoggerFactory.getLogger(WebCrawler.class);


    public WebCrawler(String baseDir) throws IOException {

        this.baseDir = "E:\\dataMAIN";
        initializeDatabase();
        initializeLogFile();
    }

    private void initializeLogFile() throws IOException {
        File logDir = new File("log");
        if (!logDir.exists()) {
            boolean created = logDir.mkdir();
            if (!created) {
                throw new IOException("Failed to create log directory.");
            }
        }
        File logFile = new File(logDir, "crawler.log");
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
        System.setProperty("logback.configurationFile", "logback.xml");
    }

    private void initializeDatabase() {
        logger.info("Initializing database...");
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS links (url TEXT PRIMARY KEY)";
            stmt.execute(sql);
            logger.info("Database initialized successfully.");
        } catch (SQLException e) {
            logger.error("Database initialization error: " + e.getMessage());
        }
    }

    public void crawl(String startUrl) {
        logger.info("Starting crawl with URL: " + startUrl);
        linksToVisit.add(startUrl);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            while (!linksToVisit.isEmpty()) {
                String url = linksToVisit.poll();
                if (shouldVisit(url)) {
                    visitedLinks.add(url);
                    saveLinkToDatabase(url);
                    try {
                        HttpGet request = new HttpGet(url);
                        try (CloseableHttpResponse response = httpClient.execute(request)) {
                            if (response.getCode() == 200) {
                                String htmlContent = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                                savePage(htmlContent, url);
                                Document doc = Jsoup.parse(htmlContent, url);
                                Elements links = doc.select("a[href]");
                                for (Element link : links) {
                                    String absUrl = link.attr("abs:href");
                                    if (shouldVisit(absUrl)) {
                                        linksToVisit.add(absUrl);
                                    }
                                }
                            } else {
                                logger.error("HTTP error fetching URL. Status=" + response.getCode() + ", URL=" + url);
                            }
                        }
                    } catch (IOException e) {
                        logger.error("IOException fetching URL: " + url, e);
                    }
                } else {
                    logger.info("URL already visited or filtered: " + url);
                }
            }
        } catch (IOException e) {
            logger.error("Error closing HttpClient.", e);
        }
        logger.info("Crawl finished.");
    }

    private void savePage(String content, String url) throws IOException {
        logger.info("Saving page: " + url);
        String sanitizedUrl = sanitizeFilename(url);
        String filename = sanitizedUrl + ".html";
        File file = new File(baseDir, filename);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        logger.info("Page saved: " + url);
    }

    private void saveLinkToDatabase(String url) {
        logger.info("Saving link to database: " + url);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO links (url) VALUES (?)")) {
            pstmt.setString(1, url);
            pstmt.executeUpdate();
            logger.info("Link saved to database: " + url);
        } catch (SQLException e) {
            logger.error("Error saving link to database: " + e.getMessage());
        }
    }

    private boolean shouldVisit(String url) {
        if (visitedLinks.contains(url) || containsFilteredKeywords(url) || !isValidUrl(url)) {
            logger.info("URL already in visited set, contains filtered keywords, or is invalid: " + url);
            return false;
        }
        logger.info("Checking if should visit URL: " + url);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM links WHERE url = ?")) {
            pstmt.setString(1, url);
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean shouldVisit = !rs.next();
                logger.info("Should visit URL " + url + ": " + shouldVisit);
                return shouldVisit;
            }
        } catch (SQLException e) {
            logger.error("Error checking link in database: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    private boolean containsFilteredKeywords(String url) {
        String[] filteredKeywords = {"login", "userlogin"};
        for (String keyword : filteredKeywords) {
            if (url.contains(keyword)) {
                logger.info("URL contains filtered keyword (" + keyword + "): " + url);
                return true;
            }
        }
        return false;
    }

    private String sanitizeFilename(String url) {
        String sanitized = url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        int maxLength = 255 - baseDir.length() - 5; // 255 - max path length, 5 - length for ".html"
        if (sanitized.length() > maxLength) {
            sanitized = hashString(url);
        }
        return sanitized;
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}