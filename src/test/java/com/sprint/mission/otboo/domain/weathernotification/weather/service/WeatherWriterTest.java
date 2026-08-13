package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WeatherWriterTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private WeatherWriter weatherWriter;

  @BeforeEach
  void setUp() {
    weatherWriter = new WeatherWriter(weatherRepository, jdbcTemplate);
  }

  @Nested
  @DisplayName("Build")
  class Build {

    @Test
    @DisplayName("전날_데이터가_없으면_diff는_null이다")
    void 전날_데이터가_없으면_diff는_null이다() {
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
      List<Weather> result = weatherWriter.build(weatherGrid, forecastedAt,
          List.of(todayForecast), Map.of());

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getTemperatureCompared()).isNull();
      assertThat(result.get(0).getHumidityCompared()).isNull();
    }

    @Test
    @DisplayName("전날_데이터가_있으면_diff를_계산한다")
    void 전날_데이터가_있으면_diff를_계산한다() {
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
      List<Weather> result = weatherWriter.build(weatherGrid, forecastedAt,
          List.of(todayForecast), Map.of(LocalDate.of(2026, 7, 26), yesterdayWeather));

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getTemperatureCompared()).isEqualTo(2.0); // 28.0 - 26.0
      assertThat(result.get(0).getHumidityCompared()).isEqualTo(5.0); // 65.0 - 60.0
    }
  }

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("build한_결과를_한_번의_배치_insert로_저장하고_한_번의_조회로_반환한다")
    void build한_결과를_한_번의_배치_insert로_저장하고_한_번의_조회로_반환한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      DailyWeatherForecastDto day1 = FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .sample();
      DailyWeatherForecastDto day2 = FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 28))
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .sample();
      Instant forecastAt1 = day1.date().atStartOfDay(KST).toInstant();
      Instant forecastAt2 = day2.date().atStartOfDay(KST).toInstant();
      Weather persisted1 = Weather.create(weatherGrid, forecastedAt, forecastAt1,
          day1.skyStatus(), day1.precipitationType(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
          WindStrength.WEAK);
      Weather persisted2 = Weather.create(weatherGrid, forecastedAt, forecastAt2,
          day2.skyStatus(), day2.precipitationType(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
          WindStrength.WEAK);
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1, 1});
      given(weatherRepository.findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(
          eq(weatherGrid), eq(forecastedAt), eq(List.of(forecastAt1, forecastAt2))))
          .willReturn(List.of(persisted1, persisted2));

      // when
      List<Weather> result = weatherWriter.save(weatherGrid, forecastedAt, List.of(day1, day2),
          Map.of());

      // then - insert는 한 번의 배치로, 조회도 한 번만 = 총 2회 왕복
      assertThat(result).containsExactly(persisted1, persisted2);
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(
          BatchPreparedStatementSetter.class);
      verify(jdbcTemplate, times(1)).batchUpdate(anyString(), setterCaptor.capture());
      assertThat(setterCaptor.getValue().getBatchSize()).isEqualTo(2);
      verify(weatherRepository, times(1))
          .findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(eq(weatherGrid),
              eq(forecastedAt), eq(List.of(forecastAt1, forecastAt2)));
    }

    @Test
    @DisplayName("전일_비교값이_null이면_JDBC에도_NULL로_바인딩한다")
    void 전일_비교값이_null이면_JDBC에도_NULL로_바인딩한다() throws Exception {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      DailyWeatherForecastDto day1 = FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      Instant forecastAt1 = day1.date().atStartOfDay(KST).toInstant();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1});
      given(weatherRepository.findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(
          eq(weatherGrid), eq(forecastedAt), eq(List.of(forecastAt1))))
          .willReturn(List.of());

      // when - Map.of()라 전일 데이터가 없어 build() 결과의 비교값이 null
      weatherWriter.save(weatherGrid, forecastedAt, List.of(day1), Map.of());

      // then
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(
          BatchPreparedStatementSetter.class);
      verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      setterCaptor.getValue().setValues(preparedStatement, 0);
      verify(preparedStatement).setObject(10, null, Types.DOUBLE);
      verify(preparedStatement).setObject(12, null, Types.DOUBLE);
    }

    @Test
    @DisplayName("예보가_없으면_배치_insert도_조회도_호출하지_않는다")
    void 예보가_없으면_배치_insert도_조회도_호출하지_않는다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");

      // when
      List<Weather> result = weatherWriter.save(weatherGrid, forecastedAt, List.of(), Map.of());

      // then
      assertThat(result).isEmpty();
      verify(jdbcTemplate, never()).batchUpdate(anyString(),
          any(BatchPreparedStatementSetter.class));
      verify(weatherRepository, never())
          .findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(any(), any(),
              any());
    }
  }
}