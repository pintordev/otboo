package com.sprint.mission.otboo.domain.weathernotification.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocationBlockCalculatorTest {

  @Nested
  @DisplayName("좌표를_블록으로_변환")
  class ToBlock {

    @Test
    @DisplayName("50m_이내로_가까운_좌표는_같은_블록으로_계산된다")
    void 오십미터_이내로_가까운_좌표는_같은_블록으로_계산된다() {
      // given
      double latitude = 37.5670000;
      double longitude = 126.9880000;
      double nearbyLatitude = 37.5670898; // 약 10m 북쪽

      // when
      BlockIndex block = LocationBlockCalculator.toBlock(latitude, longitude);
      BlockIndex nearbyBlock = LocationBlockCalculator.toBlock(nearbyLatitude, longitude);

      // then
      assertThat(nearbyBlock).isEqualTo(block);
    }
  }
}