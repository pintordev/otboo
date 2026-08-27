package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import com.sprint.mission.otboo.domain.weathernotification.sse.listener.SseRedisMessageListener;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 보장 범위: Redis Pub/Sub은 at-most-once다. 이 컨테이너가 발행 순간 구독 중이던 커넥션에만
// 메시지가 배달되고, 저장·재발행·ACK가 없다. SseMessageRepository의 재생 버퍼는 "클라이언트가
// 재연결한 경우"만 복구한다 - "이 인스턴스가 구독이 순단된 사이 발행된 메시지"는 클라이언트
// emitter가 살아있는 한 재연결 자체가 안 일어나 복구되지 않는다. 이 유실 구간을 인스턴스 재구독
// 시점에 보정하는 기능은 별도 설계가 필요해 아직 없다.
@Slf4j
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
    container.setErrorHandler(t -> log.error("SSE Redis 리스너 컨테이너 예외", t));
    return container;
  }
}