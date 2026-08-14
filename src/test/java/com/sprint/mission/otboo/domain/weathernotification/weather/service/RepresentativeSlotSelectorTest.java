package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepresentativeSlotSelectorTest {

  private final RepresentativeSlotSelector selector = new RepresentativeSlotSelector();

  @Nested
  @DisplayName("Select")
  class Select {

    @Test
    @DisplayName("D0_조회_시각과_가장_가까운_슬롯을_고른다")
    void D0_조회_시각과_가장_가까운_슬롯을_고른다() {
      // given - 조회 시각 09:30 KST, 08시/09시/11시(KST) 슬롯 중 09시가 가장 가깝다
      Weather slot08 = slot("2026-07-26T23:00:00Z"); // 08:00 KST
      Weather slot09 = slot("2026-07-27T00:00:00Z"); // 09:00 KST
      Weather slot11 = slot("2026-07-27T02:00:00Z"); // 11:00 KST
      Instant referenceInstant = Instant.parse("2026-07-27T00:30:00Z"); // 09:30 KST

      // when
      Optional<Weather> result = selector.select(List.of(slot08, slot09, slot11),
          referenceInstant);

      // then
      assertThat(result).contains(slot09);
    }

    @Test
    @DisplayName("빈_슬롯_목록이면_빈_Optional을_반환한다")
    void 빈_슬롯_목록이면_빈_Optional을_반환한다() {
      // when
      Optional<Weather> result = selector.select(List.of(), Instant.parse("2026-07-27T00:30:00Z"));

      // then
      assertThat(result).isEmpty();
    }
  }

  private Weather slot(String forecastAt) {
    WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
    return Weather.create(weatherGrid, Instant.parse("2026-07-27T08:00:00Z"),
        Instant.parse(forecastAt), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, null,
        26.0, null, 24.0, 29.0, 2.0, WindStrength.WEAK, null, null, null, null);
  }
}