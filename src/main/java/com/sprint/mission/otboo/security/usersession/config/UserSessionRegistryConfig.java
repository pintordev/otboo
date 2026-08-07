package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.decorator.ConcurrentPolicyUserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.impl.UserSessionRedisRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class UserSessionRegistryConfig {

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.impl", havingValue = "redis", matchIfMissing = true)
  public UserSessionRegistry userSessionRedisRegistry(StringRedisTemplate redisTemplate,
      UserSessionExpirationPolicy userSessionExpirationPolicy,
      ConcurrentUserSessionPolicy concurrentUserSessionPolicy, Clock clock) {
    UserSessionRegistry delegate =
        new UserSessionRedisRegistry(redisTemplate, userSessionExpirationPolicy, clock);
    return new ConcurrentPolicyUserSessionRegistry(delegate, concurrentUserSessionPolicy);
  }
}
