package com.sprint.mission.otboo.external.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionRequest;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse.Choice;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse.Message;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmExtractionFetcherTest {

  @Mock
  private LlmClient llmClient;
  @Mock
  private LlmExtractionParser llmExtractionParser;

  @Nested
  @DisplayName("Extract")
  class Extract {

    @Test
    @DisplayName("html을_요청으로_조립해_호출하고_파싱_결과를_반환한다")
    void html을_요청으로_조립해_호출하고_파싱_결과를_반환한다() {
      LlmExtractionFetcher fetcher = new LlmExtractionFetcher(llmClient, llmExtractionParser,
          "test-model");
      String html = "<html><body><h1>데님 자켓</h1></body></html>";
      LlmExtractionResponse response =
          new LlmExtractionResponse(List.of(new Choice(new Message("assistant", "{}"))));
      LlmExtractedClothesInfo info = new LlmExtractedClothesInfo("데님 자켓", null);

      given(llmClient.extract(any(LlmExtractionRequest.class))).willReturn(response);
      given(llmExtractionParser.parse(response)).willReturn(info);

      LlmExtractedClothesInfo result = fetcher.extract(html);

      assertThat(result).isEqualTo(info);
    }

    @Test
    @DisplayName("클라이언트가_FeignException을_던지면_LlmApiException으로_wrap한다")
    void 클라이언트가_FeignException을_던지면_LlmApiException으로_wrap한다() {
      LlmExtractionFetcher fetcher = new LlmExtractionFetcher(llmClient, llmExtractionParser,
          "test-model");
      String html = "<html></html>";

      FeignException feignException = FeignException.errorStatus("LlmClient#extract", response());
      given(llmClient.extract(any(LlmExtractionRequest.class))).willThrow(feignException);

      assertThatThrownBy(() -> fetcher.extract(html))
          .isInstanceOf(LlmApiException.class);
      verifyNoInteractions(llmExtractionParser);
    }

    private Request request() {
      return Request.create(Request.HttpMethod.POST, "/chat/completions", Map.of(),
          Request.Body.empty(), new RequestTemplate());
    }

    private feign.Response response() {
      return feign.Response.builder()
          .request(request())
          .status(503)
          .reason("Service Unavailable")
          .headers(Map.of())
          .build();
    }
  }
}
