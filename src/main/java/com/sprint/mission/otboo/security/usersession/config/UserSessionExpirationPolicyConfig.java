package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.AbsoluteExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SlidingExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserSessionExpirationPolicyConfig {

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.expiration-policy", havingValue = "absolute", matchIfMissing = true)
  public UserSessionExpirationPolicy absoluteUserSessionExpirationPolicy(
      UserSessionProperties userSessionProperties) {
    return new AbsoluteExpirationPolicy(userSessionProperties.userSessionExpiration());
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.expiration-policy", havingValue = "sliding")
  public UserSessionExpirationPolicy slidingUserSessionExpirationPolicy(
      UserSessionProperties userSessionProperties) {
    return new SlidingExpirationPolicy(userSessionProperties.userSessionExpiration());
  }
}
