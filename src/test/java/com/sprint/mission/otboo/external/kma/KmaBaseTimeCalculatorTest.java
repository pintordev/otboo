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
  }
}