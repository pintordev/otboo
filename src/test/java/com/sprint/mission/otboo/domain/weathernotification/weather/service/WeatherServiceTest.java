package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.HumidityDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WindSpeedDto;
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
        weatherMapper, new RepresentativeSlotSelector(), clock);
  }

  @Nested
  @DisplayName("GetWeather")
  class GetWeather {

    @Test
    @DisplayName("DB에_최신_데이터가_있으면_라이브_호출_없이_대표_슬롯을_반환한다")
    void DB에_최신_데이터가_있으면_라이브_호출_없이_대표_슬롯을_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      // 조회 시각 18:00 KST와 가장 가까운 오늘 슬롯(18시)
      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather todaySlot = Weather.create(weatherGrid, freshForecastedAt,
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE,
          0.0, 10.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null,
          null);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(todaySlot));

      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(locationNames);

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("id", todaySlot.getId())
          .set("forecastedAt", freshForecastedAt)
          .set("forecastAt", todaySlot.getForecastAt())
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(todaySlot, weatherGrid, latitude, longitude, locationNames))
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
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(
          eq(createdWeatherGrid), any())).willReturn(List.of());

      Weather savedSlot = Weather.create(createdWeatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRefresher.refreshSlots(createdWeatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of(savedSlot));

      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(savedSlot, createdWeatherGrid, latitude, longitude,
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

      // 어제(D-1) 슬롯만 존재, 오늘 슬롯은 없음(stale)
      Weather yesterdaySlot = Weather.create(weatherGrid, Instant.parse("2026-07-26T08:00:00Z"),
          Instant.parse("2026-07-26T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(yesterdaySlot));

      given(weatherRefresher.refreshSlots(weatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of());
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("stale해도_WeatherRefresher_재조회_결과가_비면_기존_DB_슬롯으로_폴백한다")
    void stale해도_WeatherRefresher_재조회_결과가_비면_기존_DB_슬롯으로_폴백한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      // 오늘 슬롯이 DB에 있지만 stale(17시 발표보다 이전에 저장된 값)
      Instant staleForecastedAt = Instant.parse("2026-07-27T05:00:00Z");
      Weather staleTodaySlot = Weather.create(weatherGrid, staleForecastedAt,
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(staleTodaySlot));

      // 라이브 재조회는 비어서 돌아온다(KMA 응답이 게이트에 전부 걸러지는 등)
      given(weatherRefresher.refreshSlots(weatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of());
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(staleTodaySlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then - 재조회가 비어도 기존 DB 슬롯을 그대로 사용해서 응답이 비지 않는다
      assertThat(result).containsExactly(expectedDto);
    }

    @Test
    @DisplayName("재조회_결과가_일부_날짜만_있어도_DB에_있던_다른_날짜_대표_슬롯은_유지된다")
    void 재조회_결과가_일부_날짜만_있어도_DB에_있던_다른_날짜_대표_슬롯은_유지된다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      // DB에 오늘(stale) 슬롯 + 내일 슬롯이 있다
      Instant staleForecastedAt = Instant.parse("2026-07-27T05:00:00Z");
      Weather staleTodaySlot = Weather.create(weatherGrid, staleForecastedAt,
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      Weather tomorrowSlot = Weather.create(weatherGrid, staleForecastedAt,
          Instant.parse("2026-07-28T06:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 24.0, 0.0, 20.0, 26.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(staleTodaySlot, tomorrowSlot));

      // 라이브 재조회는 오늘 슬롯만 갱신해서 돌아온다(내일 날짜는 파서 게이트 등으로 빠짐)
      Weather refreshedTodaySlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 29.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRefresher.refreshSlots(weatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of(refreshedTodaySlot));
      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto todayDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      WeatherDto tomorrowDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(refreshedTodaySlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(todayDto);
      given(weatherMapper.toDto(tomorrowSlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(tomorrowDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then - 오늘은 재조회 값, 내일은 DB에 있던 값이 그대로 유지되어 둘 다 반환된다
      assertThat(result).containsExactlyInAnyOrder(todayDto, tomorrowDto);
    }

    @Test
    @DisplayName("stale해서_WeatherRefresher가_반환한_결과에도_오늘_이전_슬롯은_필터링되고_날짜별_대표_슬롯만_반환된다")
    void stale해서_WeatherRefresher가_반환한_결과에도_오늘_이전_슬롯은_필터링되고_날짜별_대표_슬롯만_반환된다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of()); // stale

      // WeatherRefresher가 라이브 재조회 결과에 어제 슬롯 + 오늘 슬롯 2개(17:00/18:30 KST) + 내일 슬롯(15시 KST)를 반환해도
      Weather pastSlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-26T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK, null, null, null, null);
      Weather todayFar = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T08:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 27.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      Weather todayClosest = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:30:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      Weather tomorrowSlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-28T06:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 24.0, 0.0, 20.0, 26.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherRefresher.refreshSlots(weatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME))
          .willReturn(List.of(pastSlot, todayFar, todayClosest, tomorrowSlot));

      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto todayDto = new WeatherDto(
          todayClosest.getId(), todayClosest.getForecastedAt(), todayClosest.getForecastAt(),
          new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
              List.of("서울특별시", "중구", "명동")),
          SkyStatus.CLEAR, new PrecipitationDto(PrecipitationType.NONE, 0.0, 65.0),
          new HumidityDto(28.0, 0.0), new TemperatureDto(25.0, 0.0, 25.0, 31.0),
          new WindSpeedDto(2.0, WindStrength.WEAK));
      given(weatherMapper.toDto(todayClosest, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(todayDto);
      WeatherDto tomorrowDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(tomorrowSlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"))).willReturn(tomorrowDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then — 어제(pastSlot)는 빠지고, 오늘은 조회시각(18:00 KST)과 가장 가까운 슬롯(18:30 KST)만,
      // 내일은 대표 슬롯(15시 KST 고정) 1건만 반환된다
      assertThat(result).containsExactly(todayDto, tomorrowDto);
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