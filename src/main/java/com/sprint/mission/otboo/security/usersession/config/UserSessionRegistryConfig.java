package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.decorator.ConcurrentPolicyUserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.impl.UserSessionRedisRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class UserSessionRegistryConfig {

  @Bean
  public UserSessionRegistry userSessionRegistry(
      UserSessionProperties properties,
      StringRedisTemplate redisTemplate,
      UserSessionExpirationPolicy userSessionExpirationPolicy,
      ConcurrentUserSessionPolicy concurrentUserSessionPolicy,
      Clock clock
  ) {
    return switch (properties.impl()) {
      case REDIS -> new ConcurrentPolicyUserSessionRegistry(
          new UserSessionRedisRegistry(redisTemplate, userSessionExpirationPolicy, clock),
          concurrentUserSessionPolicy
      );
    };
  }
}
