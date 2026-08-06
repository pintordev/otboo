package com.sprint.mission.otboo.external.kma.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class KmaApiExceptionTest {

  @Nested
  @DisplayName("Of")
  class Of {

    @Test
    @DisplayName("resultCode_resultMsg가_null이어도_예외_없이_생성된다")
    void resultCode_resultMsg가_null이어도_예외_없이_생성된다() {
      // when & then
      assertThatCode(() -> KmaApiException.of(null, null)).doesNotThrowAnyException();

      KmaApiException exception = KmaApiException.of(null, null);
      assertThat(exception.getDetails())
          .containsEntry("resultCode", "UNKNOWN")
          .containsEntry("resultMsg", "UNKNOWN");
    }
  }

  @Nested
  @DisplayName("Wrap")
  class Wrap {

    @Test
    @DisplayName("원인_예외를_감싸_BAD_GATEWAY_상태의_KmaApiException을_생성한다")
    void 원인_예외를_감싸_BAD_GATEWAY_상태의_KmaApiException을_생성한다() {
      // given
      IllegalStateException cause = new IllegalStateException("커넥션 리셋");

      // when
      KmaApiException exception = KmaApiException.wrap(cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(exception.getDetails())
          .containsEntry("causeType", "IllegalStateException")
          .containsEntry("causeMessage", "커넥션 리셋");
      assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("원인_예외의_메시지가_null이면_UNKNOWN으로_기록된다")
    void 원인_예외의_메시지가_null이면_UNKNOWN으로_기록된다() {
      // given
      IllegalStateException cause = new IllegalStateException();

      // when
      KmaApiException exception = KmaApiException.wrap(cause);

      // then
      assertThat(exception.getDetails()).containsEntry("causeMessage", "UNKNOWN");
    }
  }
}