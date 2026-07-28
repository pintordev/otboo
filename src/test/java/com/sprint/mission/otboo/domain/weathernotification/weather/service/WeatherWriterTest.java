package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherWriterTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Mock
  private WeatherRepository weatherRepository;

  private WeatherWriter weatherWriter;

  @BeforeEach
  void setUp() {
    weatherWriter = new WeatherWriter(weatherRepository);
    given(weatherRepository.save(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("전날_데이터가_없으면_diff는_0으로_저장한다")
    void 전날_데이터가_없으면_diff는_0으로_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("temperatureCurrent", 28.0)
          .set("humidityCurrent", 65.0)
          .sample();

      // when
      List<Weather> result = weatherWriter.save(weatherGrid, forecastedAt,
          List.of(todayForecast), Map.of());

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getTemperatureCompared()).isEqualTo(0.0);
      assertThat(result.get(0).getHumidityCompared()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("전날_데이터가_있으면_diff를_계산해서_저장한다")
    void 전날_데이터가_있으면_diff를_계산해서_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather yesterdayWeather = Weather.create(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T00:00:00Z"),
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0,
          WindStrength.WEAK);
      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("temperatureCurrent", 28.0)
          .set("humidityCurrent", 65.0)
          .sample();

      // when
      List<Weather> result = weatherWriter.save(weatherGrid, forecastedAt,
          List.of(todayForecast), Map.of(LocalDate.of(2026, 7, 26), yesterdayWeather));

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getTemperatureCompared()).isEqualTo(2.0); // 28.0 - 26.0
      assertThat(result.get(0).getHumidityCompared()).isEqualTo(5.0); // 65.0 - 60.0
    }
  }
}