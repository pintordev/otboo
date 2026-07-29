package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.exception.InvalidCoordinateException;
import com.sprint.mission.otboo.domain.weathernotification.weather.mapper.WeatherMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();
  private static final BaseTime LATEST_BASE_TIME = new BaseTime("20260727", "1700");

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private WeatherRefresher weatherRefresher;
  @Mock
  private LocationResolver locationResolver;
  @Mock
  private WeatherMapper weatherMapper;

  private WeatherService weatherService;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherService = new WeatherService(weatherRepository, weatherRefresher, locationResolver,
        weatherMapper, clock);
  }

  @Nested
  @DisplayName("GetWeather")
  class GetWeather {

    @Test
    @DisplayName("DB에_최신_데이터가_있으면_라이브_호출_없이_반환한다")
    void DB에_최신_데이터가_있으면_라이브_호출_없이_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather todayWeather = Weather.create(weatherGrid, freshForecastedAt,
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE,
          0.0, 10.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of(todayWeather));

      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(locationNames);

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("id", todayWeather.getId())
          .set("forecastedAt", freshForecastedAt)
          .set("forecastAt", todayWeather.getForecastAt())
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(todayWeather, weatherGrid, latitude, longitude, locationNames))
          .willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).containsExactly(expectedDto);
      verifyNoInteractions(weatherRefresher);
    }

    @Test
    @DisplayName("신규_위치는_WeatherRefresher에_위임해서_반환한다")
    void 신규_위치는_WeatherRefresher에_위임해서_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;

      WeatherGrid createdWeatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(createdWeatherGrid);
      given(weatherRepository.findLatestRevisions(eq(createdWeatherGrid), any()))
          .willReturn(List.of());

      Weather savedWeather = Weather.create(createdWeatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK);
      given(weatherRefresher.refresh(createdWeatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of(savedWeather));

      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(savedWeather, createdWeatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).containsExactly(expectedDto);
    }

    @Test
    @DisplayName("기존_위치의_오늘_데이터가_stale하면_WeatherRefresher에_위임해서_반환한다")
    void 기존_위치의_오늘_데이터가_stale하면_WeatherRefresher에_위임해서_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      // 어제(D-1) 데이터만 존재, 오늘 데이터는 없음(stale)
      Weather yesterdayWeather = Weather.create(weatherGrid, Instant.parse("2026-07-26T08:00:00Z"),
          Instant.parse("2026-07-26T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(weatherGrid), any()))
          .willReturn(List.of(yesterdayWeather));

      given(weatherRefresher.refresh(weatherGrid, new KmaGridPoint(60, 127), LATEST_BASE_TIME))
          .willReturn(List.of());
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("한반도_범위를_벗어난_좌표는_InvalidCoordinateException을_던진다")
    void 한반도_범위를_벗어난_좌표는_InvalidCoordinateException을_던진다() {
      assertThatThrownBy(() -> weatherService.getWeather(10.0, 127.0))
          .isInstanceOf(InvalidCoordinateException.class);
    }
  }

  @Nested
  @DisplayName("GetLocation")
  class GetLocation {

    @Test
    @DisplayName("유효한_좌표로_조회하면_LocationDto를_반환한다")
    void 유효한_좌표로_조회하면_LocationDto를_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);
      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(locationNames);

      // when
      LocationDto result = weatherService.getLocation(latitude, longitude);

      // then
      assertThat(result).isEqualTo(
          new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
              locationNames));
    }

    @Test
    @DisplayName("한반도_범위를_벗어난_좌표는_LocationResolver_호출_없이_InvalidCoordinateException을_던진다")
    void 한반도_범위를_벗어난_좌표는_LocationResolver_호출_없이_InvalidCoordinateException을_던진다() {
      assertThatThrownBy(() -> weatherService.getLocation(10.0, 127.0))
          .isInstanceOf(InvalidCoordinateException.class);
      verifyNoInteractions(locationResolver);
    }
  }
}