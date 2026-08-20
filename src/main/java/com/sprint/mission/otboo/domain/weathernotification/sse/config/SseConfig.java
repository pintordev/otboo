package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import com.sprint.mission.otboo.domain.weathernotification.sse.listener.SseRedisMessageListener;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
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
      RedisConnectionFactory connectionFactory, SseRedisMessageListener listener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, new ChannelTopic(SSE_CHANNEL));
    return container;
  }
}