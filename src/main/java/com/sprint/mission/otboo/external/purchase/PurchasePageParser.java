package com.sprint.mission.otboo.external.purchase;

import com.sprint.mission.otboo.external.purchase.dto.PurchasePageFetchResult;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;
import com.sprint.mission.otboo.external.purchase.exception.PurchasePageFetchException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PurchasePageParser {

  private final PurchasePageClient purchasePageClient;

  public PurchasePageFetchResult fetchAndParse(String url) {
    String html = fetchPage(url);
    return new PurchasePageFetchResult(html, parse(html));
  }

  private String fetchPage(String url) {
    try {
      return purchasePageClient.fetchPage(URI.create(url));
    } catch (Exception e) {
      throw PurchasePageFetchException.wrap(e);
    }
  }

  public PurchasePageResponse parse(String html) {
    if (html == null || html.isBlank()) {
      return new PurchasePageResponse(null, null, null, null);
    }

    Document doc = Jsoup.parse(html);
    String title = getOgContent(doc, "og:title");
    String imageUrl = getOgContent(doc, "og:image");
    String description = getOgContent(doc, "og:description");
    String siteName = getOgContent(doc, "og:site_name");

    return new PurchasePageResponse(title, imageUrl, description, siteName);
  }

  private String getOgContent(Document doc, String property) {
    return doc.select("meta[property=" + property + "], meta[name=" + property + "]")
        .stream()
        .findFirst()
        .map(element -> element.attr("content"))
        .filter(content -> !content.isBlank())
        .orElse(null);
  }
}
