package com.sprint.mission.otboo.external.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse.Choice;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractionResponse.Message;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LlmExtractionParserTest {

  private final LlmExtractionParser parser = new LlmExtractionParser(new ObjectMapper());

  private static LlmExtractionResponse response(String content) {
    return new LlmExtractionResponse(List.of(new Choice(new Message("assistant", content))));
  }

  @Nested
  @DisplayName("Parse")
  class Parse {

    @Test
    @DisplayName("코드펜스_없는_JSON을_파싱한다")
    void 코드펜스_없는_JSON을_파싱한다() {
      LlmExtractionResponse response =
          response("{\"name\": \"데님 자켓\", \"imageUrl\": \"https://img/1.jpg\"}");

      LlmExtractedClothesInfo result = parser.parse(response);

      assertThat(result.name()).isEqualTo("데님 자켓");
      assertThat(result.imageUrl()).isEqualTo("https://img/1.jpg");
    }

    @Test
    @DisplayName("코드펜스로_감싼_JSON도_파싱한다")
    void 코드펜스로_감싼_JSON도_파싱한다() {
      LlmExtractionResponse response = response("```json\n{\"name\": \"후드티\", \"imageUrl\": null}\n```");

      LlmExtractedClothesInfo result = parser.parse(response);

      assertThat(result.name()).isEqualTo("후드티");
      assertThat(result.imageUrl()).isNull();
    }

    @Test
    @DisplayName("content가_비어있으면_LlmApiException을_던진다")
    void content가_비어있으면_LlmApiException을_던진다() {
      LlmExtractionResponse response = response("");

      assertThatThrownBy(() -> parser.parse(response))
          .isInstanceOf(LlmApiException.class);
    }

    @Test
    @DisplayName("JSON이_아니면_LlmApiException을_던진다")
    void JSON이_아니면_LlmApiException을_던진다() {
      LlmExtractionResponse response = response("이건 JSON이 아닙니다");

      assertThatThrownBy(() -> parser.parse(response))
          .isInstanceOf(LlmApiException.class);
    }
  }
}
