package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MaxDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MultiDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrentSessionPolicyConfig {

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.concurrent-policy", havingValue = "single", matchIfMissing = true)
  public ConcurrentUserSessionPolicy singleDeviceConcurrentUserSession() {
    return new SingleDeviceConcurrentUserSessionPolicy();
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.concurrent-policy", havingValue = "multi")
  public ConcurrentUserSessionPolicy multiDeviceConcurrentUserSessionPolicy() {
    return new MultiDeviceConcurrentUserSessionPolicy();
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.security.user-session.concurrent-policy", havingValue = "max")
  public ConcurrentUserSessionPolicy maxDeviceConcurrentUserSessionPolicy(UserSessionProperties properties) {
    return new MaxDeviceConcurrentUserSessionPolicy(properties.maxDevice());
  }
}
