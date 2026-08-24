package com.sprint.mission.otboo.external.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("external")
@SpringBootTest
class PurchasePageClientTest extends IntegrationTestSupport {

  @Autowired
  private PurchasePageClient purchasePageClient;

  @Nested
  @DisplayName("FetchPage")
  class FetchPage {

    @Test
    @DisplayName("실제_상품_페이지를_요청하면_OG_태그가_포함된_HTML을_받는다")
    void 실제_상품_페이지를_요청하면_OG_태그가_포함된_HTML을_받는다() {
      // given
      URI uri = URI.create("https://www.musinsa.com/products/3262024");

      // when
      String html = purchasePageClient.fetchPage(uri);

      // then
      assertThat(html).isNotBlank();
      assertThat(html).contains("og:title");
    }
  }
}
