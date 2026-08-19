package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AsyncConfigTest {

  private final AsyncConfig asyncConfig = new AsyncConfig();

  @Nested
  @DisplayName("mailExecutor")
  class MailExecutor {

    @Test
    @DisplayName("호출 스레드의 MDC를 실행 스레드로 전파한다")
    void 호출_스레드의_MDC를_실행_스레드로_전파한다() throws Exception {
      // given
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<String> captured = new CompletableFuture<>();

      // when
      try {
        MDC.put("requestId", requestId);
        asyncConfig.mailExecutor().execute(() -> captured.complete(MDC.get("requestId")));
      } finally {
        MDC.remove("requestId");
      }

      // then
      assertThat(captured.get(5, TimeUnit.SECONDS)).isEqualTo(requestId);
    }
  }
}