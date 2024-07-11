import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class webScaping {


    private static final String OUTPUT_FOLDER = "E:\\dataMAIN"; // папка для сохранения файлов

    public void scrapeWebsite(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        savePageContent(doc, new File(OUTPUT_FOLDER + "/" + url));

        List<String> links = getLinksFromPage(doc);
        for (String link : links) {
            if (!link.startsWith("http")) { // не обходимся ссылками на другие домены
                continue;
            }
            scrapeWebsite(link); // рекурсивно обходим все ссылки
        }
    }

    private List<String> getLinksFromPage(Document doc) {
        List<String> links = new ArrayList<>();
        for (Element element : doc.select("a[href]")) {
            String link = element.attr("href");
            if (!link.isEmpty()) { // не включаем пустые ссылки
                links.add(link);
            }
        }
        return links;
    }

    private void savePageContent(Document doc, File file) throws IOException {
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(doc.outerHtml());
        writer.close();
    }
}