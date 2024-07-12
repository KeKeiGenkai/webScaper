package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class WebCrawler {
    private Set<String> visitedLinks = new HashSet<>();
    private String baseDir;

    public WebCrawler(String baseDir) {
        this.baseDir = "E:\\dataMAIN";
    }

    public void crawl(String url) {
        if (!visitedLinks.contains(url) && isValidUrl(url)) {
            visitedLinks.add(url);
            try {
                URL resourceUrl = new URL(url);
                String contentType = resourceUrl.openConnection().getContentType();

                if (contentType != null && (contentType.startsWith("text/") || contentType.contains("xml"))) {
                    Document doc = Jsoup.connect(url).get();
                    savePage(doc, url);
                    Elements links = doc.select("a[href]");
                    for (Element link : links) {
                        String absUrl = link.attr("abs:href");
                        crawl(absUrl);
                    }
                } else {
                    saveResource(resourceUrl, url);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void savePage(Document doc, String url) throws IOException {
        String sanitizedUrl = sanitizeFilename(url);
        String filename = sanitizedUrl + ".html";
        File file = new File(baseDir, filename);
        FileUtils.writeStringToFile(file, doc.outerHtml(), "UTF-8");
    }

    private void saveResource(URL resourceUrl, String url) throws IOException {
        String sanitizedUrl = sanitizeFilename(url);
        String extension = getFileExtension(resourceUrl);
        String filename = sanitizedUrl + extension;
        File file = new File(baseDir, filename);
        FileUtils.copyURLToFile(resourceUrl, file);
    }

    private String getFileExtension(URL url) {
        String path = url.getPath();
        int lastDotIndex = path.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : path.substring(lastDotIndex);
    }

    private boolean isValidUrl(String url) {
        try {
            URL u = new URL(url);
            String protocol = u.getProtocol();
            return protocol.equals("http") || protocol.equals("https");
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String sanitizeFilename(String url) {
        String sanitized = url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        int maxLength = 255 - baseDir.length() - 5;
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
    public static void main(String[] args) {
        WebCrawler crawler = new WebCrawler("saved_pages");
        crawler.crawl("https://example.com");
    }
}
