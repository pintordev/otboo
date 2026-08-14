package com.sprint.mission.otboo.domain.weathernotification.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.LocationBlockProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocationBlockCalculatorTest {

  private LocationBlockCalculator locationBlockCalculator;

  @BeforeEach
  void setUp() {
    locationBlockCalculator = new LocationBlockCalculator(new LocationBlockProperties(500.0));
  }

  @Nested
  @DisplayName("좌표를_블록으로_변환")
  class ToBlock {

    @Test
    @DisplayName("500m_이내로_가까운_좌표는_같은_블록으로_계산된다")
    void 오백미터_이내로_가까운_좌표는_같은_블록으로_계산된다() {
      // given — 블록 경계에서 250m 안쪽인 블록 중심 좌표 기준
      double latitude = 37.5651276;
      double longitude = 126.9880000;
      double nearbyLatitude = 37.5660259; // 약 100m 북쪽

      // when
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      BlockIndex nearbyBlock = locationBlockCalculator.toBlock(nearbyLatitude, longitude);

      // then
      assertThat(nearbyBlock).isEqualTo(block);
    }

    @Test
    @DisplayName("위도가_2km_이상_떨어지면_다른_블록으로_계산된다")
    void 위도가_이천미터_이상_떨어지면_다른_블록으로_계산된다() {
      // given
      double latitude = 37.5651276;
      double longitude = 126.9880000;
      double farLatitude = 37.5830938; // 약 2000m 북쪽

      // when
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      BlockIndex farBlock = locationBlockCalculator.toBlock(farLatitude, longitude);

      // then
      assertThat(farBlock.latBlock()).isNotEqualTo(block.latBlock());
      assertThat(farBlock.lonBlock()).isEqualTo(block.lonBlock());
    }

    @Test
    @DisplayName("경도가_2km_이상_떨어지면_다른_블록으로_계산된다")
    void 경도가_이천미터_이상_떨어지면_다른_블록으로_계산된다() {
      // given — 블록 경계에서 안쪽인 블록 중심 좌표 기준
      double latitude = 37.5651276;
      double longitude = 126.9900665;
      double farLongitude = 127.0124165; // 약 2000m 동쪽

      // when
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      BlockIndex farBlock = locationBlockCalculator.toBlock(latitude, farLongitude);

      // then
      assertThat(farBlock.lonBlock()).isNotEqualTo(block.lonBlock());
      assertThat(farBlock.latBlock()).isEqualTo(block.latBlock());
    }
  }
}