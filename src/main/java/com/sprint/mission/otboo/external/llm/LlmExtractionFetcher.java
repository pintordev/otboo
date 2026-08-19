package com.sprint.mission.otboo.external.llm;

import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionRequest;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionRequest.LlmMessage;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import feign.FeignException;
import java.util.List;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmExtractionFetcher {

  private static final int MAX_BODY_TEXT_CHARS = 3000;
  private static final String SYSTEM_PROMPT = """
      당신은 쇼핑몰 상품 페이지의 HTML을 분석하는 전문가입니다.
      HTML에서 의상 정보를 추출하여 아래 JSON 형식으로만 응답하세요.
      다른 텍스트는 절대 포함하지 마세요.
      {"name": "상품명", "imageUrl": "이미지URL"}
      """;

  private final LlmClient llmClient;
  private final LlmExtractionParser llmExtractionParser;
  private final String model;

  public LlmExtractionFetcher(LlmClient llmClient, LlmExtractionParser llmExtractionParser,
      @Value("${external.llm.model}") String model) {
    this.llmClient = llmClient;
    this.llmExtractionParser = llmExtractionParser;
    this.model = model;
  }

  public LlmExtractedClothesInfo extract(String html) {
    String bodyText = extractBodyText(html);
    String userPrompt = "다음 텍스트에서 의상 정보를 추출하세요:\n"
        + bodyText.substring(0, Math.min(bodyText.length(), MAX_BODY_TEXT_CHARS));

    LlmExtractionRequest request = new LlmExtractionRequest(
        model,
        List.of(
            new LlmMessage("system", SYSTEM_PROMPT),
            new LlmMessage("user", userPrompt)
        )
    );

    LlmExtractionResponse response;
    try {
      response = llmClient.extract(request);
    } catch (FeignException e) {
      throw LlmApiException.callFailed(e);
    }

    return llmExtractionParser.parse(response);
  }

  private String extractBodyText(String html) {
    var doc = Jsoup.parse(html);
    doc.select("script, style").remove();
    return doc.body() != null ? doc.body().text() : "";
  }
}
