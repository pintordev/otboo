package com.sprint.mission.otboo.security.cookie.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshTokenCookieProperties")
class RefreshTokenCookiePropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static RefreshTokenCookieProperties properties(Duration refreshTokenExpiration, Boolean secure) {
    return new RefreshTokenCookieProperties(refreshTokenExpiration, secure);
  }

  @Nested
  @DisplayName("refreshTokenExpiration 검증")
  class RefreshTokenExpiration {

    @Test
    @DisplayName("성공_1초_이상이면_위반이_없다")
    void 성공_1초_이상이면_위반이_없다() {
      // given
      RefreshTokenCookieProperties props = properties(Duration.ofDays(14), false);

      // when
      Set<ConstraintViolation<RefreshTokenCookieProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_null이면_위반이_발생한다")
    void 실패_null이면_위반이_발생한다() {
      // given
      RefreshTokenCookieProperties props = properties(null, false);

      // when
      Set<ConstraintViolation<RefreshTokenCookieProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("실패_1초_미만이면_위반이_발생한다")
    void 실패_1초_미만이면_위반이_발생한다() {
      // given
      RefreshTokenCookieProperties props = properties(Duration.ofMillis(999), false);

      // when
      Set<ConstraintViolation<RefreshTokenCookieProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("secure 검증")
  class Secure {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      RefreshTokenCookieProperties props = properties(Duration.ofDays(14), true);

      // when
      Set<ConstraintViolation<RefreshTokenCookieProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_null이면_위반이_발생한다")
    void 실패_null이면_위반이_발생한다() {
      // given
      RefreshTokenCookieProperties props = properties(Duration.ofDays(14), null);

      // when
      Set<ConstraintViolation<RefreshTokenCookieProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
