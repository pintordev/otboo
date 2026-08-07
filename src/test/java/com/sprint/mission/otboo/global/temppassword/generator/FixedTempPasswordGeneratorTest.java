package com.sprint.mission.otboo.global.temppassword.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FixedTempPasswordGenerator")
class FixedTempPasswordGeneratorTest {

  private final FixedTempPasswordGenerator generator = new FixedTempPasswordGenerator();

  @Nested
  @DisplayName("고정 임시 비밀번호 생성 - generate")
  class Generate {

    @Test
    @DisplayName("항상 고정된 값을 반환한다")
    void 항상_고정된_값을_반환한다() {
      // when
      String first = generator.generate();
      String second = generator.generate();

      // then
      assertThat(first).isEqualTo("temporary1!!");
      assertThat(second).isEqualTo("temporary1!!");
    }
  }
}
