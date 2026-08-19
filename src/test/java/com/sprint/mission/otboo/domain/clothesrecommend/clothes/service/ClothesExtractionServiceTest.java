package com.sprint.mission.otboo.domain.clothesrecommend.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesExtractionFailedException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper.ClothesMapper;
import com.sprint.mission.otboo.external.llm.LlmExtractionFetcher;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import com.sprint.mission.otboo.external.purchase.PurchasePageParser;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageFetchResult;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageResponse;
import com.sprint.mission.otboo.external.purchase.exception.PurchasePageFetchException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClothesExtractionServiceTest {

  @InjectMocks
  ClothesExtractionService clothesExtractionService;

  @Mock
  PurchasePageParser purchasePageParser;
  @Mock
  LlmExtractionFetcher llmExtractionFetcher;
  @Mock
  ClothesMapper clothesMapper;

  @Nested
  @DisplayName("ExtractByUrl")
  class ExtractByUrl {

    @Test
    @DisplayName("OG_태그가_있으면_ClothesDto를_반환한다")
    void OG_태그가_있으면_ClothesDto를_반환한다() {
      // given
      String url = "https://www.musinsa.com/products/12345";
      PurchasePageResponse ogResult = new PurchasePageResponse(
          "데님 자켓", "https://image.musinsa.com/goods/001.jpg", null, null);
      PurchasePageFetchResult fetchResult = new PurchasePageFetchResult("<html></html>", ogResult);
      ClothesDto expected = new ClothesDto(null, null, "데님 자켓",
          "https://image.musinsa.com/goods/001.jpg", null, List.of());

      given(purchasePageParser.fetchAndParse(url)).willReturn(fetchResult);
      given(clothesMapper.toDto(ogResult)).willReturn(expected);

      // when
      ClothesDto result = clothesExtractionService.extractByUrl(url);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("OG_태그_실패시_LLM으로_추출한다")
    void OG_태그_실패시_LLM으로_추출한다() {
      // given
      String url = "https://www.musinsa.com/products/12345";
      String html = "<html><body><h1>데님 자켓</h1></body></html>";
      PurchasePageResponse emptyOg = new PurchasePageResponse(null, null, null, null);
      PurchasePageFetchResult fetchResult = new PurchasePageFetchResult(html, emptyOg);
      LlmExtractedClothesInfo info = new LlmExtractedClothesInfo("데님 자켓", null);
      ClothesDto expected = new ClothesDto(null, null, "데님 자켓", null, null, List.of());

      given(purchasePageParser.fetchAndParse(url)).willReturn(fetchResult);
      given(llmExtractionFetcher.extract(html)).willReturn(info);
      given(clothesMapper.toDto(info)).willReturn(expected);

      // when
      ClothesDto result = clothesExtractionService.extractByUrl(url);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("PurchasePageFetchException을_ClothesExtractionFailedException으로_변환한다")
    void PurchasePageFetchException을_ClothesExtractionFailedException으로_변환한다() {
      // given
      String url = "https://www.musinsa.com/products/12345";
      given(purchasePageParser.fetchAndParse(url))
          .willThrow(PurchasePageFetchException.wrap(new RuntimeException("Connection refused")));

      // when & then
      assertThatThrownBy(() -> clothesExtractionService.extractByUrl(url))
          .isInstanceOf(ClothesExtractionFailedException.class);
    }

    @Test
    @DisplayName("LlmApiException을_ClothesExtractionFailedException으로_변환한다")
    void LlmApiException을_ClothesExtractionFailedException으로_변환한다() {
      // given
      String url = "https://www.musinsa.com/products/12345";
      String html = "<html><body>상품</body></html>";
      PurchasePageResponse emptyOg = new PurchasePageResponse(null, null, null, null);
      PurchasePageFetchResult fetchResult = new PurchasePageFetchResult(html, emptyOg);

      given(purchasePageParser.fetchAndParse(url)).willReturn(fetchResult);
      given(llmExtractionFetcher.extract(html)).willThrow(LlmApiException.parseFailed());

      // when & then
      assertThatThrownBy(() -> clothesExtractionService.extractByUrl(url))
          .isInstanceOf(ClothesExtractionFailedException.class);
    }
  }
}
