package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.AbsoluteExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SlidingExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserSessionExpirationPolicyConfig {

  @Bean
  public UserSessionExpirationPolicy expirationPolicy(UserSessionProperties properties) {
    return switch (properties.expirationPolicy()) {
      case ABSOLUTE -> new AbsoluteExpirationPolicy(properties.userSessionExpiration());
      case SLIDING -> new SlidingExpirationPolicy(properties.userSessionExpiration());
    };
  }
}
