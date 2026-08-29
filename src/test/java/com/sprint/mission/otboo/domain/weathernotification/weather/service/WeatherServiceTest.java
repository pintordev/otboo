package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
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
  @Mock
  private WeatherCacheProvider weatherCacheProvider;
  @Mock
  private Executor weatherRefreshExecutor;
  @Mock
  private Executor kakaoLocationExecutor;

  private WeatherService weatherService;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherService = new WeatherService(weatherRepository, weatherRefresher, locationResolver,
        weatherMapper, new RepresentativeSlotSelector(), clock, weatherCacheProvider,
        weatherRefreshExecutor, kakaoLocationExecutor);
    // weatherRefreshExecutor는 CompletableFuture.supplyAsync()에 직접 쓰이므로,
    // 목이 실제로 실행해 주지 않으면 future가 영영 완료되지 않는다
    lenient().doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(weatherRefreshExecutor).execute(any());
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
          null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
      given(weatherMapper.toDto(todaySlot, weatherGrid, latitude, longitude, locationNames, true))
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
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      given(weatherRefresher.refreshSlots(createdWeatherGrid, new KmaGridPoint(60, 127),
          LATEST_BASE_TIME)).willReturn(List.of(savedSlot));

      given(locationResolver.resolveLocationNames(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동"));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(savedSlot, createdWeatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"), true)).willReturn(expectedDto);

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
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
          List.of("서울특별시", "중구", "명동"), true)).willReturn(expectedDto);

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
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      Weather tomorrowSlot = Weather.create(weatherGrid, staleForecastedAt,
          Instant.parse("2026-07-28T06:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 24.0, 0.0, 20.0, 26.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(staleTodaySlot, tomorrowSlot));

      // 라이브 재조회는 오늘 슬롯만 갱신해서 돌아온다(내일 날짜는 파서 게이트 등으로 빠짐)
      Weather refreshedTodaySlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 29.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
          List.of("서울특별시", "중구", "명동"), true)).willReturn(todayDto);
      given(weatherMapper.toDto(tomorrowSlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"), false)).willReturn(tomorrowDto);

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
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      Weather todayFar = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T08:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 27.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      Weather todayClosest = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:30:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      Weather tomorrowSlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-28T06:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 24.0, 0.0, 20.0, 26.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
          List.of("서울특별시", "중구", "명동"), true)).willReturn(todayDto);
      WeatherDto tomorrowDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(tomorrowSlot, weatherGrid, latitude, longitude,
          List.of("서울특별시", "중구", "명동"), false)).willReturn(tomorrowDto);

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

  @Nested
  @DisplayName("GetLocationAsync")
  class GetLocationAsync {

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
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.completedFuture(locationNames));

      // when
      LocationDto result = weatherService.getLocationAsync(latitude, longitude).join();

      // then
      assertThat(result).isEqualTo(
          new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
              locationNames));
    }

    @Test
    @DisplayName("위치_조회가_실패하면_빈_지역명으로_폴백한다")
    void 위치_조회가_실패하면_빈_지역명으로_폴백한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("카카오 호출 실패")));

      // when
      LocationDto result = weatherService.getLocationAsync(latitude, longitude).join();

      // then
      assertThat(result).isEqualTo(
          new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
              List.of()));
    }
  }

  @Nested
  @DisplayName("GetWeatherAsync")
  class GetWeatherAsync {

    @Test
    @DisplayName("캐시가_fresh이면_DB와_재조회_없이_캐시_값으로_응답한다")
    void 캐시가_fresh이면_DB와_재조회_없이_캐시_값으로_응답한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather cachedSlot = ENTITY_FIXTURE_MONKEY.giveMeBuilder(Weather.class)
          .set("weatherGrid", weatherGrid)
          .set("forecastedAt", freshForecastedAt)
          .set("forecastAt", Instant.parse("2026-07-27T09:00:00Z"))
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .set("precipitationAmount", 0.0)
          .set("precipitationProbability", 10.0)
          .set("humidityCurrent", 65.0)
          .set("humidityCompared", 0.0)
          .set("temperatureCurrent", 28.0)
          .set("temperatureCompared", 0.0)
          .set("temperatureMin", 25.0)
          .set("temperatureMax", 31.0)
          .set("windSpeed", 2.0)
          .set("windAsWord", WindStrength.WEAK)
          .set("baselineTemperatureCurrent", null)
          .set("baselinePrecipitationType", null)
          .set("baselinePrecipitationProbability", null)
          .set("baselinePrecipitationAmount", null)
          .sample();
      given(weatherCacheProvider.findCachedSlots(weatherGrid)).willReturn(List.of(cachedSlot));

      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.completedFuture(locationNames));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(cachedSlot, weatherGrid, latitude, longitude, locationNames, true))
          .willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeatherAsync(latitude, longitude).join();

      // then
      assertThat(result).containsExactly(expectedDto);
      verifyNoInteractions(weatherRepository, weatherRefresher);
    }

    @Test
    @DisplayName("캐시는_stale이지만_DB가_fresh이면_캐시만_채우고_재조회는_안_한다")
    void 캐시는_stale이지만_DB가_fresh이면_캐시만_채우고_재조회는_안_한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);
      given(weatherCacheProvider.findCachedSlots(weatherGrid)).willReturn(List.of());

      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather dbSlot = Weather.create(weatherGrid, freshForecastedAt,
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE,
          0.0, 10.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null,
          null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(dbSlot));

      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.completedFuture(locationNames));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(dbSlot, weatherGrid, latitude, longitude, locationNames, true))
          .willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeatherAsync(latitude, longitude).join();

      // then
      assertThat(result).containsExactly(expectedDto);
      verifyNoInteractions(weatherRefresher);
      verify(weatherCacheProvider).putSlots(weatherGrid, List.of(dbSlot));
    }

    @Test
    @DisplayName("캐시와_DB_모두_stale이면_WeatherRefresher로_재조회한다")
    void 캐시와_DB_모두_stale이면_WeatherRefresher로_재조회한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);
      given(weatherCacheProvider.findCachedSlots(weatherGrid)).willReturn(List.of());
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of());

      Weather refreshedSlot = Weather.create(weatherGrid, LATEST_BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      given(weatherRefresher.refreshSlotsAsync(eq(weatherGrid), eq(new KmaGridPoint(60, 127)),
          eq(LATEST_BASE_TIME), eq(List.of()), eq(weatherRefreshExecutor)))
          .willReturn(CompletableFuture.completedFuture(List.of(refreshedSlot)));

      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.completedFuture(locationNames));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(refreshedSlot, weatherGrid, latitude, longitude, locationNames,
          true)).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeatherAsync(latitude, longitude).join();

      // then
      assertThat(result).containsExactly(expectedDto);
    }

    @Test
    @DisplayName("날씨_재조회만_실패해도_이미_성공한_위치명은_유지된다")
    void 날씨_재조회만_실패해도_이미_성공한_위치명은_유지된다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);
      given(weatherCacheProvider.findCachedSlots(weatherGrid)).willReturn(List.of());

      Instant staleForecastedAt = Instant.parse("2026-07-27T05:00:00Z");
      Weather staleDbSlot = Weather.create(weatherGrid, staleForecastedAt,
          Instant.parse("2026-07-27T09:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null,
          SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(staleDbSlot));

      // 날씨 재조회(기상청)는 실패한다
      given(weatherRefresher.refreshSlotsAsync(eq(weatherGrid), eq(new KmaGridPoint(60, 127)),
          eq(LATEST_BASE_TIME), eq(List.of(staleDbSlot)), eq(weatherRefreshExecutor)))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("기상청 응답 실패")));

      // 위치명 조회(Kakao)는 성공한다
      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.completedFuture(locationNames));

      WeatherDto expectedDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .sample();
      given(weatherMapper.toDto(staleDbSlot, weatherGrid, latitude, longitude, locationNames,
          true)).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeatherAsync(latitude, longitude).join();

      // then - 날씨 재조회만 실패했으므로 DB 슬롯으로 폴백하되, 이미 성공한 위치명은
      // 빈 값으로 대체되지 않고 그대로 매퍼에 전달돼야 한다
      assertThat(result).containsExactly(expectedDto);
    }

    @Test
    @DisplayName("위치명_조회만_실패해도_이미_성공한_날씨_슬롯은_유지된다")
    void 위치명_조회만_실패해도_이미_성공한_날씨_슬롯은_유지된다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(locationResolver.resolveWeatherGrid(new KmaGridPoint(60, 127)))
          .willReturn(weatherGrid);

      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather cachedSlot = ENTITY_FIXTURE_MONKEY.giveMeBuilder(Weather.class)
          .set("weatherGrid", weatherGrid)
          .set("forecastedAt", freshForecastedAt)
          .set("forecastAt", Instant.parse("2026-07-27T09:00:00Z"))
          .set("skyStatus", SkyStatus.CLEAR)
          .set("precipitationType", PrecipitationType.NONE)
          .set("precipitationAmount", 0.0)
          .set("precipitationProbability", 10.0)
          .set("humidityCurrent", 65.0)
          .set("humidityCompared", 0.0)
          .set("temperatureCurrent", 28.0)
          .set("temperatureCompared", 0.0)
          .set("temperatureMin", 25.0)
          .set("temperatureMax", 31.0)
          .set("windSpeed", 2.0)
          .set("windAsWord", WindStrength.WEAK)
          .set("baselineTemperatureCurrent", null)
          .set("baselinePrecipitationType", null)
          .set("baselinePrecipitationProbability", null)
          .set("baselinePrecipitationAmount", null)
          .sample();
      given(weatherCacheProvider.findCachedSlots(weatherGrid)).willReturn(List.of(cachedSlot));

      // 위치명 조회(Kakao)는 실패한다
      given(locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("카카오 호출 실패")));

      WeatherDto expectedDto = new WeatherDto(
          cachedSlot.getId(), cachedSlot.getForecastedAt(), cachedSlot.getForecastAt(),
          new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(), List.of()),
          SkyStatus.CLEAR, new PrecipitationDto(PrecipitationType.NONE, 0.0, 10.0),
          new HumidityDto(65.0, 0.0), new TemperatureDto(28.0, 0.0, 25.0, 31.0),
          new WindSpeedDto(2.0, WindStrength.WEAK));
      given(weatherMapper.toDto(cachedSlot, weatherGrid, latitude, longitude, List.of(), true))
          .willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeatherAsync(latitude, longitude).join();

      // then - 위치명 조회만 실패했으므로 빈 위치명으로 폴백하되, 이미 성공한 날씨 슬롯(캐시)은
      // DB 재조회 없이 그대로 매퍼에 전달돼야 한다
      assertThat(result).containsExactly(expectedDto);
      verifyNoInteractions(weatherRepository, weatherRefresher);
    }
  }
}