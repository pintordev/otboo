package com.sprint.mission.otboo.external.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.llm.dto.LlmChatRequest;
import com.sprint.mission.otboo.external.llm.dto.LlmChatRequest.LlmMessage;
import com.sprint.mission.otboo.external.llm.dto.LlmChatResponse;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("external")
@SpringBootTest
class LlmClientTest extends IntegrationTestSupport {

  @Autowired
  private LlmClient llmClient;

  @Value("${external.llm.model}")
  private String model;

  @Nested
  @DisplayName("Chat")
  class Chat {

    @Test
    @DisplayName("실제_OpenRouter_API를_호출하면_응답_스키마에_맞는_결과를_받는다")
    void 실제_OpenRouter_API를_호출하면_응답_스키마에_맞는_결과를_받는다() {
      // given
      LlmChatRequest request = new LlmChatRequest(
          model,
          List.of(new LlmMessage("user", "숫자 1만 정확히 출력해."))
      );

      // when
      LlmChatResponse response = llmClient.chat(request);

      // then
      assertThat(response.choices()).isNotEmpty();
      assertThat(response.getContent()).isNotBlank();
    }
  }
}
