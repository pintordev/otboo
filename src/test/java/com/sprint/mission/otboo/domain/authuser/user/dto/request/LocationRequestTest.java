package com.sprint.mission.otboo.domain.authuser.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocationRequest")
class LocationRequestTest {

  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static LocationRequest request(List<String> locationNames) {
    return new LocationRequest(37.5, 127.0, 60, 127, locationNames);
  }

  @Nested
  @DisplayName("locationNames 개수 검증")
  class LocationNamesSize {

    @Test
    @DisplayName("성공_4개면_위반이_없다")
    void 성공_4개면_위반이_없다() {
      // given
      LocationRequest props = request(List.of("서울특별시", "중구", "명동", ""));

      // when
      Set<ConstraintViolation<LocationRequest>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("실패_4개_미만이면_위반이_발생한다")
    void 실패_4개_미만이면_위반이_발생한다() {
      // given
      LocationRequest props = request(List.of("서울특별시", "중구", "명동"));

      // when
      Set<ConstraintViolation<LocationRequest>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("실패_4개_초과면_위반이_발생한다")
    void 실패_4개_초과면_위반이_발생한다() {
      // given
      LocationRequest props = request(List.of("서울특별시", "중구", "명동", "", "여의도동"));

      // when
      Set<ConstraintViolation<LocationRequest>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("locationNames 필수 값 검증")
  class LocationNamesRequired {

    @Test
    @DisplayName("실패_null이면_위반이_발생한다")
    void 실패_null이면_위반이_발생한다() {
      // given
      LocationRequest props = request(null);

      // when
      Set<ConstraintViolation<LocationRequest>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("실패_내부에_null이_포함되면_위반이_발생한다")
    void 실패_내부에_null이_포함되면_위반이_발생한다() {
      // given
      LocationRequest props = request(Arrays.asList("서울특별시", "중구", null, ""));

      // when
      Set<ConstraintViolation<LocationRequest>> violations = VALIDATOR.validate(props);

      // then
      assertThat(violations).isNotEmpty();
    }
  }
}
