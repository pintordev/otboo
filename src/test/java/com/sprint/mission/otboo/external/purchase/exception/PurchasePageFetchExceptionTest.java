package com.sprint.mission.otboo.external.purchase.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("구매 페이지 조회 실패 예외")
class PurchasePageFetchExceptionTest {

  @Nested
  @DisplayName("팩토리")
  class Factory {

    @Test
    @DisplayName("wrap은 원인 예외를 유지하고 502 상태를 가진다")
    void wrap은_원인_예외를_유지하고_502_상태를_가진다() {
      // given
      Throwable cause = new RuntimeException("연결 실패");

      // when
      PurchasePageFetchException exception = PurchasePageFetchException.wrap(cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(exception.getMessage()).isEqualTo("구매 페이지를 불러올 수 없습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isEqualTo(cause);
    }
  }
}
