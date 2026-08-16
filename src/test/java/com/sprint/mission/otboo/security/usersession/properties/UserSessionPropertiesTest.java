package com.sprint.mission.otboo.security.usersession.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.properties.enums.ConcurrentPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.ExpirationPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.UserSessionRegistryType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("UserSessionProperties")
class UserSessionPropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static UserSessionProperties properties(Duration userSessionExpiration) {
    return new UserSessionProperties(userSessionExpiration, 3, ExpirationPolicyType.ABSOLUTE,
        ConcurrentPolicyType.MULTI, UserSessionRegistryType.REDIS);
  }

  @Nested
  class UserSessionExpiration {

    @Test
    void 성공_1초_이상이면_위반이_없다() {
      // given
      UserSessionProperties props = properties(Duration.ofDays(14));

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 실패_null이면_위반이_발생한다() {
      // given
      UserSessionProperties props = properties(null);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_1초_미만이면_위반이_발생한다() {
      // given
      UserSessionProperties props = properties(Duration.ofMillis(999));

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  class MaxDevicesRequiredWhenMax {

    @Test
    void 실패_concurrentPolicy가_max인데_maxDevices가_없으면_위반이_발생한다() {
      // given
      UserSessionProperties props = new UserSessionProperties(Duration.ofDays(14), null,
          ExpirationPolicyType.ABSOLUTE, ConcurrentPolicyType.MAX, UserSessionRegistryType.REDIS);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 성공_concurrentPolicy가_max이고_maxDevices가_있으면_위반이_없다() {
      // given
      UserSessionProperties props = new UserSessionProperties(Duration.ofDays(14), 3,
          ExpirationPolicyType.ABSOLUTE, ConcurrentPolicyType.MAX, UserSessionRegistryType.REDIS);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 성공_concurrentPolicy가_max가_아니면_maxDevices가_없어도_위반이_없다() {
      // given
      UserSessionProperties props = new UserSessionProperties(Duration.ofDays(14), null,
          ExpirationPolicyType.ABSOLUTE, ConcurrentPolicyType.MULTI, UserSessionRegistryType.REDIS);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }
  }

  @Nested
  @DisplayName("설정값 바인딩 실패")
  class EnumBindingFailure {

    @Configuration
    @EnableConfigurationProperties(UserSessionProperties.class)
    static class TestConfig {

    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues("otboo.security.user-session.user-session-expiration=14d");

    @Test
    @DisplayName("concurrentPolicy가 지원하지 않는 값이면 컨텍스트 기동에 실패한다")
    void 실패_concurrentPolicy가_지원하지_않는_값이면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues("otboo.security.user-session.concurrent-policy=unknown")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("expirationPolicy가 지원하지 않는 값이면 컨텍스트 기동에 실패한다")
    void 실패_expirationPolicy가_지원하지_않는_값이면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues("otboo.security.user-session.expiration-policy=unknown")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("impl이 지원하지 않는 값이면 컨텍스트 기동에 실패한다")
    void 실패_impl이_지원하지_않는_값이면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues("otboo.security.user-session.impl=unknown")
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
