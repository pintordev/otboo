package com.sprint.mission.otboo.global.temppassword.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.temppassword.generator.impl.RandomTempPasswordGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RandomTempPasswordGenerator")
class RandomTempPasswordGeneratorTest {

  private static final String ALLOWED_CHARS =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";

  private final RandomTempPasswordGenerator generator = new RandomTempPasswordGenerator();

  @Nested
  @DisplayName("임시 비밀번호 생성 - generate")
  class Generate {

    @Test
    @DisplayName("길이가 12자인 문자열을 생성한다")
    void 길이가_12자인_문자열을_생성한다() {
      // when
      String password = generator.generate();

      // then
      assertThat(password).hasSize(12);
    }

    @Test
    @DisplayName("생성된 문자열은 혼동되기 쉬운 문자(I, O, l, 0, 1)를 포함하지 않는다")
    void 생성된_문자열은_혼동되기_쉬운_문자를_포함하지_않는다() {
      // when
      String password = generator.generate();

      // then
      assertThat(password.chars().mapToObj(c -> (char) c))
          .allMatch(c -> ALLOWED_CHARS.indexOf(c) >= 0);
    }

    @Test
    @DisplayName("연속으로 호출하면 매번 다른 값을 생성한다")
    void 연속으로_호출하면_매번_다른_값을_생성한다() {
      // when
      long distinctCount = IntStream.range(0, 20)
          .mapToObj(i -> generator.generate())
          .distinct()
          .count();

      // then
      assertThat(distinctCount).isEqualTo(20);
    }
  }
}
