package com.sprint.mission.otboo.global.init;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdminProperties")
class AdminPropertiesTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static AdminProperties properties(String name, String email, String password) {
    return new AdminProperties(name, email, password);
  }

  @Nested
  @DisplayName("name 검증")
  class Name {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      AdminProperties props = properties("최고 관리자", "admin@otboo.us", "admin-password1");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_blank이면_위반이_발생한다")
    void 실패_blank이면_위반이_발생한다() {
      // given
      AdminProperties props = properties("   ", "admin@otboo.us", "admin-password1");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("email 검증")
  class Email {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      AdminProperties props = properties("최고 관리자", "admin@otboo.us", "admin-password1");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_blank이면_위반이_발생한다")
    void 실패_blank이면_위반이_발생한다() {
      // given
      AdminProperties props = properties("최고 관리자", "   ", "admin-password1");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("password 검증")
  class Password {

    @Test
    @DisplayName("성공_값이_있으면_위반이_없다")
    void 성공_값이_있으면_위반이_없다() {
      // given
      AdminProperties props = properties("최고 관리자", "admin@otboo.us", "admin-password1");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_blank이면_위반이_발생한다")
    void 실패_blank이면_위반이_발생한다() {
      // given
      AdminProperties props = properties("최고 관리자", "admin@otboo.us", "   ");

      // when
      Set<ConstraintViolation<AdminProperties>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
