package com.sprint.mission.otboo.security.usersession.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.AbsoluteExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import com.sprint.mission.otboo.security.usersession.registry.decorator.ConcurrentPolicyUserSessionRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

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
  class RedisSelection {

    @Test
    void 성공_impl프로퍼티가_redis이면_UserSessionRegistry빈이_ConcurrentPolicyUserSessionRegistry로_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.impl=redis").run(context -> {
        // then
        assertThat(context).hasSingleBean(UserSessionRegistry.class);
        assertThat(context.getBean(UserSessionRegistry.class))
            .isInstanceOf(ConcurrentPolicyUserSessionRegistry.class);
      });
    }

    @Test
    void 성공_프로퍼티가_없으면_기본값으로_UserSessionRegistry빈이_등록된다() {
      // given & when
      contextRunner.run(context -> {
        // then
        assertThat(context).hasSingleBean(UserSessionRegistry.class);
        assertThat(context.getBean(UserSessionRegistry.class))
            .isInstanceOf(ConcurrentPolicyUserSessionRegistry.class);
      });
    }
  }

  @Nested
  class InvalidSelection {

    @Test
    void 실패_impl프로퍼티가_알수없는_값이면_UserSessionRegistry빈이_등록되지_않는다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.impl=unknown").run(context -> {
        // then
        assertThat(context).doesNotHaveBean(UserSessionRegistry.class);
      });
    }
  }
}
