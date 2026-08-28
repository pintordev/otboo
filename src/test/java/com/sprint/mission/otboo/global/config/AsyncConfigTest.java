package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  private final AsyncConfig asyncConfig = new AsyncConfig();

  private ThreadPoolTaskExecutor executor;

  @AfterEach
  void tearDown() {
    try {
      if (executor != null) {
        executor.shutdown();
      }
    } finally {
      MDC.clear();
    }
  }

  @Nested
  @DisplayName("mailExecutor")
  class MailExecutor {

    @Test
    @DisplayName("호출 스레드의 MDC를 실행 스레드로 전파한다")
    void 호출_스레드의_MDC를_실행_스레드로_전파한다() throws Exception {
      // given
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<String> captured = new CompletableFuture<>();
      executor = (ThreadPoolTaskExecutor) asyncConfig.mailExecutor();

      // when
      MDC.put("requestId", requestId);
      executor.execute(() -> captured.complete(MDC.get("requestId")));

      // then
      assertThat(captured.get(5, TimeUnit.SECONDS)).isEqualTo(requestId);
    }
  }

  @Nested
  @DisplayName("sseDisconnectExecutor")
  class SseDisconnectExecutor {

    @Test
    @DisplayName("호출 스레드의 MDC를 실행 스레드로 전파한다")
    void 호출_스레드의_MDC를_실행_스레드로_전파한다() throws Exception {
      // given
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<String> captured = new CompletableFuture<>();
      executor = (ThreadPoolTaskExecutor) asyncConfig.sseDisconnectExecutor();

      // when
      MDC.put("requestId", requestId);
      executor.execute(() -> captured.complete(MDC.get("requestId")));

      // then
      assertThat(captured.get(5, TimeUnit.SECONDS)).isEqualTo(requestId);
    }
  }

  @Nested
  @DisplayName("sseListenerExecutor")
  class SseListenerExecutor {

    @Test
    @DisplayName("큐 포화 시 CallerRunsPolicy로 역압을 건다")
    void 큐_포화_시_CallerRunsPolicy로_역압을_건다() {
      // when
      executor = (ThreadPoolTaskExecutor) asyncConfig.sseListenerExecutor();

      // then
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }
  }

  @Nested
  @DisplayName("dmListenerExecutor")
  class DmListenerExecutor {

    @Test
    @DisplayName("호출 스레드의 MDC를 실행 스레드로 전파한다")
    void 호출_스레드의_MDC를_실행_스레드로_전파한다() throws Exception {
      // given
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<String> captured = new CompletableFuture<>();
      executor = (ThreadPoolTaskExecutor) asyncConfig.dmListenerExecutor();

      // when
      MDC.put("requestId", requestId);
      executor.execute(() -> captured.complete(MDC.get("requestId")));

      // then
      assertThat(captured.get(5, TimeUnit.SECONDS)).isEqualTo(requestId);
    }
  }

  @Nested
  @DisplayName("feedIndexExecutor")
  class FeedIndexExecutor {

    @Test
    @DisplayName("호출 스레드의 MDC를 실행 스레드로 전파한다")
    void 호출_스레드의_MDC를_실행_스레드로_전파한다() throws Exception {
      // given
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<String> captured = new CompletableFuture<>();
      executor = (ThreadPoolTaskExecutor) asyncConfig.feedIndexExecutor();

      // when
      MDC.put("requestId", requestId);
      executor.execute(() -> captured.complete(MDC.get("requestId")));

      // then
      assertThat(captured.get(5, TimeUnit.SECONDS)).isEqualTo(requestId);
    }

    @Test
    @DisplayName("큐가 차면 호출 스레드에서 실행해 색인 유실을 막는다")
    void 큐가_차면_호출_스레드에서_실행해_색인_유실을_막는다() {
      // given
      executor = (ThreadPoolTaskExecutor) asyncConfig.feedIndexExecutor();

      // when & then
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }
  }
}
