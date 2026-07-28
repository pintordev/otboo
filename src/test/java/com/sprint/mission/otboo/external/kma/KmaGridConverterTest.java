package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaGridConverterTest {

  @Nested
  @DisplayName("위경도를_격자로_변환")
  class ToGrid {

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

    @Test
    @DisplayName("한반도_범위를_벗어난_위도는_예외가_발생한다")
    void 한반도_범위를_벗어난_위도는_예외가_발생한다() {
      assertThatThrownBy(() -> KmaGridConverter.toGrid(10.0, 127.0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("한반도_범위를_벗어난_경도는_예외가_발생한다")
    void 한반도_범위를_벗어난_경도는_예외가_발생한다() {
      assertThatThrownBy(() -> KmaGridConverter.toGrid(36.0, 100.0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NaN_위도는_예외가_발생한다")
    void NaN_위도는_예외가_발생한다() {
      assertThatThrownBy(() -> KmaGridConverter.toGrid(Double.NaN, 127.0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NaN_경도는_예외가_발생한다")
    void NaN_경도는_예외가_발생한다() {
      assertThatThrownBy(() -> KmaGridConverter.toGrid(36.0, Double.NaN))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}