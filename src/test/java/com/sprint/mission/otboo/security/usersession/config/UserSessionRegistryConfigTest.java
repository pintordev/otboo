package com.sprint.mission.otboo.security.usersession.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.AbsoluteExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import com.sprint.mission.otboo.security.usersession.properties.enums.ConcurrentPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.ExpirationPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.UserSessionRegistryType;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.decorator.ConcurrentPolicyUserSessionRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@DisplayName("UserSessionRegistryConfig")
class UserSessionRegistryConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(UserSessionRegistryConfig.class)
      .withBean(Clock.class, Clock::systemUTC)
      .withBean(StringRedisTemplate.class,
          () -> new StringRedisTemplate(mock(RedisConnectionFactory.class)))
      .withBean(UserSessionExpirationPolicy.class,
          () -> new AbsoluteExpirationPolicy(Duration.ofDays(14)))
      .withBean(ConcurrentUserSessionPolicy.class, SingleDeviceConcurrentUserSessionPolicy::new);

  @Nested
  @DisplayName("impl 값이 redis일 때")
  class RedisSelection {

    @Test
    @DisplayName("ConcurrentPolicyUserSessionRegistry 빈이 등록된다")
    void 성공_impl값이_redis이면_ConcurrentPolicyUserSessionRegistry가_등록된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class, () -> new UserSessionProperties(
              Duration.ofDays(14), null, ExpirationPolicyType.ABSOLUTE,
              ConcurrentPolicyType.SINGLE, UserSessionRegistryType.REDIS))
          .run(context -> {
            // then
            assertThat(context).hasSingleBean(UserSessionRegistry.class);
            assertThat(context.getBean(UserSessionRegistry.class))
                .isInstanceOf(ConcurrentPolicyUserSessionRegistry.class);
          });
    }

    @Test
    @DisplayName("impl 값이 없으면 기본값으로 등록된다")
    void 성공_impl값이_없으면_기본값으로_등록된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class, () -> new UserSessionProperties(
              Duration.ofDays(14), null, ExpirationPolicyType.ABSOLUTE,
              ConcurrentPolicyType.SINGLE, null))
          .run(context -> {
            // then
            assertThat(context).hasSingleBean(UserSessionRegistry.class);
            assertThat(context.getBean(UserSessionRegistry.class))
                .isInstanceOf(ConcurrentPolicyUserSessionRegistry.class);
          });
    }
  }
}
