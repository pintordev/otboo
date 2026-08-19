package com.sprint.mission.otboo.external.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.external.purchase.dto.PurchasePageFetchResult;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;
import com.sprint.mission.otboo.external.purchase.exception.PurchasePageFetchException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchasePageParserTest {

  @Mock
  private PurchasePageClient purchasePageClient;

  private PurchasePageParser parser;

  @BeforeEach
  void setUp() {
    parser = new PurchasePageParser(purchasePageClient);
  }

  @Nested
  @DisplayName("Parse")
  class Parse {

    @Test
    @DisplayName("OG 태그가 모두 있는 HTML → 정상 파싱")
    void parse_withFullOgTags_returnsResponse() {
      String html = """
          <html>
          <head>
            <meta property="og:title" content="슬림핏 데님 자켓" />
            <meta property="og:image" content="https://image.musinsa.com/images/goods/001.jpg" />
            <meta property="og:description" content="클래식한 데님 자켓입니다." />
            <meta property="og:site_name" content="무신사" />
          </head>
          <body></body>
          </html>
          """;

      PurchasePageResponse result = parser.parse(html);

      assertThat(result.title()).isEqualTo("슬림핏 데님 자켓");
      assertThat(result.imageUrl()).isEqualTo("https://image.musinsa.com/images/goods/001.jpg");
      assertThat(result.description()).isEqualTo("클래식한 데님 자켓입니다.");
      assertThat(result.siteName()).isEqualTo("무신사");
      assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("OG 태그가 없는 HTML → 빈 결과")
    void parse_withoutOgTags_returnsEmpty() {
      String html = """
          <html>
          <head><title>상품 페이지</title></head>
          <body><h1>데님 자켓</h1></body>
          </html>
          """;

      PurchasePageResponse result = parser.parse(html);

      assertThat(result.title()).isNull();
      assertThat(result.imageUrl()).isNull();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("빈 HTML → 빈 결과")
    void parse_withEmptyHtml_returnsEmpty() {
      String html = "";

      PurchasePageResponse result = parser.parse(html);

      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("일부 OG 태그만 있는 HTML → 있는 것만 채움")
    void parse_withPartialOgTags_returnsPartial() {
      String html = """
          <html>
          <head>
            <meta property="og:title" content="오버사이즈 후드티" />
          </head>
          <body></body>
          </html>
          """;

      PurchasePageResponse result = parser.parse(html);

      assertThat(result.title()).isEqualTo("오버사이즈 후드티");
      assertThat(result.imageUrl()).isNull();
      assertThat(result.isEmpty()).isFalse();
    }
  }

  @Nested
  @DisplayName("FetchAndParse")
  class FetchAndParse {

    @Test
    @DisplayName("정상 응답이면 html과 파싱 결과를 함께 반환한다")
    void fetchAndParse_success_returnsHtmlAndParsedResult() {
      String url = "https://www.musinsa.com/products/12345";
      String html = """
          <html><head><meta property="og:title" content="데님 자켓" /></head></html>
          """;
      given(purchasePageClient.fetchPage(URI.create(url))).willReturn(html);

      PurchasePageFetchResult result = parser.fetchAndParse(url);

      assertThat(result.html()).isEqualTo(html);
      assertThat(result.ogResult().title()).isEqualTo("데님 자켓");
    }

    @Test
    @DisplayName("클라이언트가 예외를 던지면 PurchasePageFetchException으로 wrap한다")
    void fetchAndParse_clientThrows_wrapsAsPurchasePageFetchException() {
      String url = "https://www.musinsa.com/products/12345";
      given(purchasePageClient.fetchPage(URI.create(url)))
          .willThrow(new RuntimeException("Connection refused"));

      assertThatThrownBy(() -> parser.fetchAndParse(url))
          .isInstanceOf(PurchasePageFetchException.class);
    }
  }
}
