package com.sprint.mission.otboo.domain.weathernotification.weather.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WeatherMapperTest {

  private final WeatherMapper weatherMapper = new WeatherMapper();

  @Nested
  @DisplayName("ToDto")
  class ToDto {

    @Test
    @DisplayName("Weather와_Location을_WeatherDto로_정확히_변환한다")
    void Weather와_Location을_WeatherDto로_정확히_변환한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather weather = Weather.create(
          weatherGrid,
          Instant.parse("2026-07-27T08:00:00Z"),
          Instant.parse("2026-07-27T00:00:00Z"),
          SkyStatus.CLEAR,
          PrecipitationType.NONE,
          0.0,
          10.0,
          65.0,
          2.0,
          28.0,
          -1.5,
          25.0,
          31.0,
          2.5,
          WindStrength.WEAK
      , null, null, null, null);

      double requestLatitude = 37.1234567;
      double requestLongitude = 127.1234567;
      List<String> requestLocationNames = List.of("서울특별시", "종로구", "청운동");

      // when
      WeatherDto dto = weatherMapper.toDto(weather, weatherGrid, requestLatitude, requestLongitude,
          requestLocationNames);

      // then
      assertThat(dto.forecastedAt()).isEqualTo(Instant.parse("2026-07-27T08:00:00Z"));
      assertThat(dto.forecastAt()).isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
      assertThat(dto.location().latitude()).isEqualTo(requestLatitude);
      assertThat(dto.location().longitude()).isEqualTo(requestLongitude);
      assertThat(dto.location().x()).isEqualTo(60);
      assertThat(dto.location().y()).isEqualTo(127);
      assertThat(dto.location().locationNames()).containsExactly("서울특별시", "종로구", "청운동");
      assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.NONE);
      assertThat(dto.precipitation().probability()).isEqualTo(10.0);
      assertThat(dto.humidity().current()).isEqualTo(65.0);
      assertThat(dto.humidity().comparedToDayBefore()).isEqualTo(2.0);
      assertThat(dto.temperature().current()).isEqualTo(28.0);
      assertThat(dto.temperature().comparedToDayBefore()).isEqualTo(-1.5);
      assertThat(dto.temperature().min()).isEqualTo(25.0);
      assertThat(dto.temperature().max()).isEqualTo(31.0);
      assertThat(dto.windSpeed().speed()).isEqualTo(2.5);
      assertThat(dto.windSpeed().asWord()).isEqualTo(WindStrength.WEAK);
    }
  }
}