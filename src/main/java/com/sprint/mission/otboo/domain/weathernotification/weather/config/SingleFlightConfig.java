package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.domain.weathernotification.weather.singleflight.SingleFlightRegistry;
import com.sprint.mission.otboo.external.kakao.KakaoFeignProperties;
import com.sprint.mission.otboo.external.kma.KmaFeignProperties;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// KmaFeignProperties/KakaoFeignProperties를 여기서 등록하는 이유: 두 프로퍼티는 원래 각자의
// 외부 API 패키지 소관이지만, SingleFlightLeaseTimeoutValidator가 이 프로퍼티들과
// SingleFlightProperties.lockTtl의 관계를 기동 시점에 검증하려면 셋 다 빈으로 존재해야 한다.
@Configuration
@EnableConfigurationProperties({
    SingleFlightProperties.class, KmaFeignProperties.class, KakaoFeignProperties.class
})
public class SingleFlightConfig {

  public static final String SINGLE_FLIGHT_CHANNEL_PATTERN = "single-flight:*";

  @Bean
  public RedisMessageListenerContainer singleFlightListenerContainer(
      RedisConnectionFactory connectionFactory, SingleFlightRegistry registry,
      @Qualifier("singleFlightListenerExecutor") Executor singleFlightListenerExecutor) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setTaskExecutor(singleFlightListenerExecutor);
    container.addMessageListener(registry, new PatternTopic(SINGLE_FLIGHT_CHANNEL_PATTERN));
    return container;
  }
}