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
    @DisplayName("원인_예외를_감싸_BAD_GATEWAY_상태의_KmaApiException을_생성하되_공개_details에는_원인_정보를_남기지_않는다")
    void 원인_예외를_감싸_BAD_GATEWAY_상태의_KmaApiException을_생성하되_공개_details에는_원인_정보를_남기지_않는다() {
      // given
      IllegalStateException cause = new IllegalStateException("커넥션 리셋");

      // when
      KmaApiException exception = KmaApiException.wrap(cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      // details는 GlobalExceptionHandler를 거쳐 공개 ErrorResponse로 그대로 직렬화되므로,
      // 원인 예외의 타입/메시지 같은 내부 정보를 담지 않는다 - 원인은 initCause()로만 보관되어
      // 서버 로그(log.error 등)에서만 확인 가능
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isSameAs(cause);
    }
  }
}