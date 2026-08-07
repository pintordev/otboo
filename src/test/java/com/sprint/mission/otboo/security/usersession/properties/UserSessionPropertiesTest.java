package com.sprint.mission.otboo.security.usersession.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserSessionPropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  @Nested
  class UserSessionExpiration {

    @Test
    void 성공_1초_이상이면_위반이_없다() {
      // given
      UserSessionProperties props = new UserSessionProperties(Duration.ofDays(14), 3);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 실패_null이면_위반이_발생한다() {
      // given
      UserSessionProperties props = new UserSessionProperties(null, 3);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_1초_미만이면_위반이_발생한다() {
      // given
      UserSessionProperties props = new UserSessionProperties(Duration.ofMillis(999), 3);

      // when
      Set<ConstraintViolation<UserSessionProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
