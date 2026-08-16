package com.sprint.mission.otboo.security.usersession.config;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MaxDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MultiDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrentSessionPolicyConfig {

  @Bean
  public ConcurrentUserSessionPolicy concurrentPolicy(UserSessionProperties properties) {
    return switch (properties.concurrentPolicy()) {
      case MULTI -> new MultiDeviceConcurrentUserSessionPolicy();
      case SINGLE -> new SingleDeviceConcurrentUserSessionPolicy();
      case MAX -> new MaxDeviceConcurrentUserSessionPolicy(properties.maxDevices());
    };
  }
}
