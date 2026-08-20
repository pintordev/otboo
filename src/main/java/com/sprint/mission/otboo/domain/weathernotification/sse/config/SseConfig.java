package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import com.sprint.mission.otboo.domain.weathernotification.sse.listener.SseRedisMessageListener;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@EnableConfigurationProperties(SseReplayBufferProperties.class)
public class SseConfig {

  public static final String SSE_CHANNEL = "sse:notifications";

  @Bean
  public RedisMessageListenerContainer sseMessageListenerContainer(
      RedisConnectionFactory connectionFactory, SseRedisMessageListener listener,
      @Qualifier("sseListenerExecutor") Executor sseListenerExecutor) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, new ChannelTopic(SSE_CHANNEL));
    container.setTaskExecutor(sseListenerExecutor);
    return container;
  }
}