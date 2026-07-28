package com.sprint.mission.otboo.external.kma.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}