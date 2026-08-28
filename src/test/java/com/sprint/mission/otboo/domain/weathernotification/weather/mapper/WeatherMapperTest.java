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
      , null, null, null, null, SkyStatus.CLOUDY, PrecipitationType.RAIN, 90.0);

      double requestLatitude = 37.1234567;
      double requestLongitude = 127.1234567;
      List<String> requestLocationNames = List.of("서울특별시", "종로구", "청운동");

      // when - 오늘(isToday=true)이므로 일별 요약값(CLOUDY/RAIN/90.0)이 아니라 그 슬롯의 실제값을 써야 한다
      WeatherDto dto = weatherMapper.toDto(weather, weatherGrid, requestLatitude, requestLongitude,
          requestLocationNames, true);

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

    @Test
    @DisplayName("오늘이_아니면_그날_요약값(최악_하늘상태_최다_강수형태_최대_습도)을_사용한다")
    void 오늘이_아니면_그날_요약값을_사용한다() {
      // given - 슬롯 실제값(CLEAR/NONE/65.0)과 다른 일별 요약값(CLOUDY/RAIN/90.0)
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather weather = Weather.create(
          weatherGrid,
          Instant.parse("2026-07-28T08:00:00Z"),
          Instant.parse("2026-07-28T15:00:00Z"),
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
      , null, null, null, null, SkyStatus.CLOUDY, PrecipitationType.RAIN, 90.0);

      // when
      WeatherDto dto = weatherMapper.toDto(weather, weatherGrid, 37.1234567, 127.1234567,
          List.of(), false);

      // then
      assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLOUDY);
      assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.RAIN);
      assertThat(dto.humidity().current()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("오늘이_아니어도_일별_요약_컬럼이_null이면_실제_슬롯값으로_폴백한다")
    void 오늘이_아니어도_일별_요약_컬럼이_null이면_실제_슬롯값으로_폴백한다() {
      // given - 아직 배치가 안 돌아 일별 요약 컬럼이 채워지기 전(마이그레이션 직후) 상황
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather weather = Weather.create(
          weatherGrid,
          Instant.parse("2026-07-28T08:00:00Z"),
          Instant.parse("2026-07-28T15:00:00Z"),
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
      , null, null, null, null, null, null, null);

      // when
      WeatherDto dto = weatherMapper.toDto(weather, weatherGrid, 37.1234567, 127.1234567,
          List.of(), false);

      // then
      assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.NONE);
      assertThat(dto.humidity().current()).isEqualTo(65.0);
    }
  }
}