package com.sprint.mission.otboo.security.oauth2.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OAuth2Properties")
class OAuth2PropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static OAuth2Properties properties(String successRedirectUri, String failureRedirectUri) {
    return new OAuth2Properties(successRedirectUri, failureRedirectUri);
  }

  @Nested
  @DisplayName("successRedirectUri 검증")
  class SuccessRedirectUri {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      OAuth2Properties props = properties("https://otboo.us/oauth/success", "https://otboo.us/oauth/failure");

      // when
      Set<ConstraintViolation<OAuth2Properties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_blank이면_위반이_발생한다")
    void 실패_blank이면_위반이_발생한다() {
      // given
      OAuth2Properties props = properties("   ", "https://otboo.us/oauth/failure");

      // when
      Set<ConstraintViolation<OAuth2Properties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("failureRedirectUri 검증")
  class FailureRedirectUri {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      OAuth2Properties props = properties("https://otboo.us/oauth/success", "https://otboo.us/oauth/failure");

      // when
      Set<ConstraintViolation<OAuth2Properties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_blank이면_위반이_발생한다")
    void 실패_blank이면_위반이_발생한다() {
      // given
      OAuth2Properties props = properties("https://otboo.us/oauth/success", "   ");

      // when
      Set<ConstraintViolation<OAuth2Properties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
