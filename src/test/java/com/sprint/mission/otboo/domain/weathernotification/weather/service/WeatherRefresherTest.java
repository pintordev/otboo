package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
class WeatherRefresherTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();
  private static final BaseTime BASE_TIME = new BaseTime("20260727", "1700");
  private static final KmaGridPoint GRID = new KmaGridPoint(60, 127);

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private KmaForecastFetcher kmaForecastFetcher;
  @Mock
  private WeatherWriter weatherWriter;

  private WeatherRefresher weatherRefresher;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherRefresher = new WeatherRefresher(weatherRepository, kmaForecastFetcher, weatherWriter,
        clock);
  }

  @Nested
  @DisplayName("Refresh")
  class Refresh {

    @Test
    @DisplayName("신규_격자는_빈_existingByDate로_기상청_예보를_조회해서_저장한다")
    void 신규_격자는_빈_existingByDate로_기상청_예보를_조회해서_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of());

      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      given(kmaForecastFetcher.fetch(GRID, BASE_TIME, Instant.parse("2026-07-27T09:00:00Z")))
          .willReturn(List.of(todayForecast));

      Weather savedWeather = Weather.create(weatherGrid, BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK);
      given(weatherWriter.save(weatherGrid, BASE_TIME.toInstant(), List.of(todayForecast),
          Map.of())).willReturn(List.of(savedWeather));

      // when
      List<Weather> result = weatherRefresher.refresh(weatherGrid, GRID, BASE_TIME);

      // then
      assertThat(result).containsExactly(savedWeather);
    }

    @Test
    @DisplayName("전날_최신_리비전을_existingByDate에_포함해서_저장한다")
    void 전날_최신_리비전을_existingByDate에_포함해서_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather yesterdayWeather = Weather.create(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T00:00:00Z"),
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0,
          WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of(yesterdayWeather));

      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      given(kmaForecastFetcher.fetch(GRID, BASE_TIME, Instant.parse("2026-07-27T09:00:00Z")))
          .willReturn(List.of(todayForecast));
      given(weatherWriter.save(any(), any(), any(), any())).willReturn(List.of());

      // when
      weatherRefresher.refresh(weatherGrid, GRID, BASE_TIME);

      // then
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<LocalDate, Weather>> existingByDateCaptor =
          ArgumentCaptor.forClass(Map.class);
      verify(weatherWriter).save(eq(weatherGrid), eq(BASE_TIME.toInstant()),
          eq(List.of(todayForecast)), existingByDateCaptor.capture());
      assertThat(existingByDateCaptor.getValue())
          .containsEntry(LocalDate.of(2026, 7, 26), yesterdayWeather);
    }
  }

  @Nested
  @DisplayName("Build")
  class Build {

    @Test
    @DisplayName("신규_격자는_빈_existingByDate로_기상청_예보를_조회해서_build만_위임한다")
    void 신규_격자는_빈_existingByDate로_기상청_예보를_조회해서_build만_위임한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of());

      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      given(kmaForecastFetcher.fetch(GRID, BASE_TIME, Instant.parse("2026-07-27T09:00:00Z")))
          .willReturn(List.of(todayForecast));

      Weather builtWeather = Weather.create(weatherGrid, BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK);
      given(weatherWriter.build(weatherGrid, BASE_TIME.toInstant(), List.of(todayForecast),
          Map.of())).willReturn(List.of(builtWeather));

      // when
      List<Weather> result = weatherRefresher.build(weatherGrid, GRID, BASE_TIME);

      // then
      assertThat(result).containsExactly(builtWeather);
    }

    @Test
    @DisplayName("전날_최신_리비전을_existingByDate에_포함해서_build를_위임한다")
    void 전날_최신_리비전을_existingByDate에_포함해서_build를_위임한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather yesterdayWeather = Weather.create(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T00:00:00Z"),
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0,
          WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of(yesterdayWeather));

      DailyWeatherForecastDto todayForecast = FIXTURE_MONKEY.giveMeBuilder(
              DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      given(kmaForecastFetcher.fetch(GRID, BASE_TIME, Instant.parse("2026-07-27T09:00:00Z")))
          .willReturn(List.of(todayForecast));
      given(weatherWriter.build(any(), any(), any(), any())).willReturn(List.of());

      // when
      weatherRefresher.build(weatherGrid, GRID, BASE_TIME);

      // then
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<LocalDate, Weather>> existingByDateCaptor =
          ArgumentCaptor.forClass(Map.class);
      verify(weatherWriter).build(eq(weatherGrid), eq(BASE_TIME.toInstant()),
          eq(List.of(todayForecast)), existingByDateCaptor.capture());
      assertThat(existingByDateCaptor.getValue())
          .containsEntry(LocalDate.of(2026, 7, 26), yesterdayWeather);
    }
  }
}