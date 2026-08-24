package com.sprint.mission.otboo.external.llm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("LLM API 예외")
class LlmApiExceptionTest {

  @Nested
  @DisplayName("팩토리")
  class Factory {

    @Test
    @DisplayName("callFailed는 원인 예외를 유지하고 502 상태를 가진다")
    void callFailed는_원인_예외를_유지하고_502_상태를_가진다() {
      // given
      Throwable cause = new RuntimeException("호출 실패");

      // when
      LlmApiException exception = LlmApiException.callFailed(cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(exception.getMessage()).isEqualTo("LLM 응답 처리에 실패했습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("parseFailed는 원인 예외가 없고 502 상태를 가진다")
    void parseFailed는_원인_예외가_없고_502_상태를_가진다() {
      // when
      LlmApiException exception = LlmApiException.parseFailed();

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(exception.getMessage()).isEqualTo("LLM 응답 처리에 실패했습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isNull();
    }
  }
}
