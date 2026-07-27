package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaBaseTimeCalculatorTest {

  @Nested
  @DisplayName("Calculate")
  class Calculate {

    @Test
    @DisplayName("18시_정각이면_17시_발표를_최신_발표시각으로_계산한다")
    void 십팔시_정각이면_십칠시_발표를_최신_발표시각으로_계산한다() {
      // given - 2026-07-27 18:00 KST
      Instant now = Instant.parse("2026-07-27T09:00:00Z");

      // when
      BaseTime baseTime = KmaBaseTimeCalculator.calculate(now);

      // then
      assertThat(baseTime.baseDate()).isEqualTo("20260727");
      assertThat(baseTime.baseTime()).isEqualTo("1700");
    }

    @Test
    @DisplayName("자정_직후면_전날_23시_발표로_계산한다")
    void 자정_직후면_전날_23시_발표로_계산한다() {
      // given - 2026-07-27 01:30 KST
      Instant now = Instant.parse("2026-07-26T16:30:00Z");

      // when
      BaseTime baseTime = KmaBaseTimeCalculator.calculate(now);

      // then
      assertThat(baseTime.baseDate()).isEqualTo("20260726");
      assertThat(baseTime.baseTime()).isEqualTo("2300");
    }
  }

  @Nested
  @DisplayName("BaseTimeToInstant")
  class BaseTimeToInstant {

    @Test
    @DisplayName("17시_발표는_KST_17시_Instant로_변환된다")
    void 십칠시_발표는_KST_십칠시_Instant로_변환된다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "1700");

      // when
      Instant instant = baseTime.toInstant();

      // then - 2026-07-27 17:00 KST = 2026-07-27T08:00:00Z
      assertThat(instant).isEqualTo(Instant.parse("2026-07-27T08:00:00Z"));
    }
  }
}