package com.sprint.mission.otboo.security.token.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenPropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
  private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[32]);
  private static final String SHORT_SECRET = Base64.getEncoder().encodeToString(new byte[20]);

  private static TokenProperties properties(Duration accessExpiration, Duration refreshExpiration,
      String accessSecret, String refreshSecret) {
    return new TokenProperties(accessExpiration, refreshExpiration, accessSecret, refreshSecret);
  }

  @Nested
  class AccessTokenExpiration {

    @Test
    void 성공_15분에서_1시간_사이면_위반이_없다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 성공_경계값_15분과_1시간은_허용된다() {
      // given
      TokenProperties lowerBound = properties(Duration.ofMinutes(15), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);
      TokenProperties upperBound = properties(Duration.ofHours(1), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> lowerViolations = VALIDATOR.validate(lowerBound);
      Set<ConstraintViolation<TokenProperties>> upperViolations = VALIDATOR.validate(upperBound);

      // then
      assertThat(lowerViolations).isEmpty();
      assertThat(upperViolations).isEmpty();
    }

    @Test
    void 실패_null이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(null, Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_15분_미만이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(14), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_1시간_초과면_위반이_발생한다() {
      // given
      TokenProperties props = properties(
          Duration.ofHours(1).plusSeconds(1), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  class RefreshTokenExpiration {

    @Test
    void 성공_1일에서_30일_사이면_위반이_없다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(10), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 성공_경계값_1일과_30일은_허용된다() {
      // given
      TokenProperties lowerBound = properties(Duration.ofMinutes(30), Duration.ofDays(1), VALID_SECRET, VALID_SECRET);
      TokenProperties upperBound = properties(Duration.ofMinutes(30), Duration.ofDays(30), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> lowerViolations = VALIDATOR.validate(lowerBound);
      Set<ConstraintViolation<TokenProperties>> upperViolations = VALIDATOR.validate(upperBound);

      // then
      assertThat(lowerViolations).isEmpty();
      assertThat(upperViolations).isEmpty();
    }

    @Test
    void 실패_null이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), null, VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_1일_미만이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(
          Duration.ofMinutes(30), Duration.ofDays(1).minusSeconds(1), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_30일_초과면_위반이_발생한다() {
      // given
      TokenProperties props = properties(
          Duration.ofMinutes(30), Duration.ofDays(30).plusSeconds(1), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  class AccessSecret {

    @Test
    void 성공_44자_이상이면_위반이_없다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 실패_blank이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), "   ", VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_44자_미만이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), SHORT_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  class RefreshSecret {

    @Test
    void 성공_44자_이상이면_위반이_없다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), VALID_SECRET, VALID_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    void 실패_blank이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), VALID_SECRET, "   ");

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    void 실패_44자_미만이면_위반이_발생한다() {
      // given
      TokenProperties props = properties(Duration.ofMinutes(30), Duration.ofDays(14), VALID_SECRET, SHORT_SECRET);

      // when
      Set<ConstraintViolation<TokenProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
