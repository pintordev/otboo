package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.domain.weathernotification.weather.singleflight.SingleFlightRegistry;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class SingleFlightConfig {

  public static final String SINGLE_FLIGHT_CHANNEL_PATTERN = "single-flight:*";

  @Bean
  public RedisMessageListenerContainer singleFlightListenerContainer(
      RedisConnectionFactory connectionFactory, SingleFlightRegistry registry,
      @Qualifier("sseListenerExecutor") Executor sseListenerExecutor) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setTaskExecutor(sseListenerExecutor);
    container.addMessageListener(registry, new PatternTopic(SINGLE_FLIGHT_CHANNEL_PATTERN));
    return container;
  }
}