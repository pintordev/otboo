package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.domain.weathernotification.sse.listener.SseRedisMessageListener;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SseConfigTest {

  private final SseConfig sseConfig = new SseConfig();

  @Nested
  @DisplayName("sseMessageListenerContainer")
  class SseMessageListenerContainerBean {

    @Test
    @DisplayName("기본 SimpleAsyncTaskExecutor 대신 바운드 ThreadPoolTaskExecutor를 사용한다")
    void 기본_SimpleAsyncTaskExecutor_대신_바운드_ThreadPoolTaskExecutor를_사용한다()
        throws Exception {
      // given
      RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
      SseRedisMessageListener listener = mock(SseRedisMessageListener.class);

      // when
      RedisMessageListenerContainer container =
          sseConfig.sseMessageListenerContainer(connectionFactory, listener);

      // then
      Field taskExecutorField = RedisMessageListenerContainer.class
          .getDeclaredField("taskExecutor");
      taskExecutorField.setAccessible(true);
      assertThat(taskExecutorField.get(container)).isInstanceOf(ThreadPoolTaskExecutor.class);
    }
  }
}