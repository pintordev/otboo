package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaGridConverterTest {

  @Nested
  @DisplayName("위경도를_격자로_변환")
  class 위경도를_격자로_변환 {

    @Test
    @DisplayName("서울_중구_좌표는_기상청_격자_nx_60_ny_127로_변환된다")
    void 서울_중구_좌표는_기상청_격자_nx_60_ny_127로_변환된다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;

      // when
      KmaGridPoint point = KmaGridConverter.toGrid(latitude, longitude);

      // then
      assertThat(point.nx()).isEqualTo(60);
      assertThat(point.ny()).isEqualTo(127);
    }
  }
}