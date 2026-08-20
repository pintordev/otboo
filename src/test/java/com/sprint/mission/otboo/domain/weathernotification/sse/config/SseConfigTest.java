package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.domain.weathernotification.sse.listener.SseRedisMessageListener;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class SseConfigTest {

  private final SseConfig sseConfig = new SseConfig();

  @Nested
  @DisplayName("sseMessageListenerContainer")
  class SseMessageListenerContainerBean {

    @Test
    @DisplayName("주입받은 taskExecutor를 그대로 사용한다")
    void 주입받은_taskExecutor를_그대로_사용한다() throws Exception {
      // given
      RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
      SseRedisMessageListener listener = mock(SseRedisMessageListener.class);
      Executor sseListenerExecutor = mock(Executor.class);

      // when
      RedisMessageListenerContainer container = sseConfig.sseMessageListenerContainer(
          connectionFactory, listener, sseListenerExecutor);

      // then
      Field taskExecutorField = RedisMessageListenerContainer.class
          .getDeclaredField("taskExecutor");
      taskExecutorField.setAccessible(true);
      assertThat(taskExecutorField.get(container)).isSameAs(sseListenerExecutor);
    }
  }
}