package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.singleflight.SingleFlightRegistry;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherRefresherTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();
  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();
  private static final BaseTime BASE_TIME = new BaseTime("20260727", "1700");
  private static final KmaGridPoint GRID = new KmaGridPoint(60, 127);

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private KmaForecastFetcher kmaForecastFetcher;
  @Mock
  private WeatherWriter weatherWriter;
  @Mock
  private SingleFlightRegistry singleFlightRegistry;
  @Mock
  private WeatherCacheProvider weatherCacheProvider;
  private final RepresentativeSlotSelector representativeSlotSelector =
      new RepresentativeSlotSelector();

  private WeatherRefresher weatherRefresher;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherRefresher = new WeatherRefresher(weatherRepository, kmaForecastFetcher, weatherWriter,
        clock, singleFlightRegistry, weatherCacheProvider, representativeSlotSelector);
    logger = (Logger) LoggerFactory.getLogger(WeatherRefresher.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }


  @Nested
  @DisplayName("RefreshSlots")
  class RefreshSlots {

    @Test
    @DisplayName("신규_격자는_빈_existingBySlot으로_기상청_슬롯_예보를_조회해서_저장한다")
    void 신규_격자는_빈_existingBySlot으로_기상청_슬롯_예보를_조회해서_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of());

      WeatherForecastSlotDto todaySlot = FIXTURE_MONKEY.giveMeBuilder(
              WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .sample();
      given(kmaForecastFetcher.fetchSlots(GRID, BASE_TIME)).willReturn(List.of(todaySlot));

      Weather savedWeather = Weather.create(weatherGrid, BASE_TIME.toInstant(),
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK, null, null, null, null);
      given(weatherWriter.saveSlots(weatherGrid, BASE_TIME.toInstant(), List.of(todaySlot),
          Map.of())).willReturn(List.of(savedWeather));

      // when
      List<Weather> result = weatherRefresher.refreshSlots(weatherGrid, GRID, BASE_TIME);

      // then
      assertThat(result).containsExactly(savedWeather);
    }

    @Test
    @DisplayName("기존_슬롯을_existingBySlot에_forecastAt_기준으로_포함해서_저장한다")
    void 기존_슬롯을_existingBySlot에_forecastAt_기준으로_포함해서_저장한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Weather yesterdaySlot = Weather.create(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T00:00:00Z"),
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0,
          WindStrength.WEAK, null, null, null, null);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of(yesterdaySlot));

      WeatherForecastSlotDto todaySlot = FIXTURE_MONKEY.giveMeBuilder(
              WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .sample();
      given(kmaForecastFetcher.fetchSlots(GRID, BASE_TIME)).willReturn(List.of(todaySlot));
      given(weatherWriter.saveSlots(any(), any(), any(), any())).willReturn(List.of());

      // when
      weatherRefresher.refreshSlots(weatherGrid, GRID, BASE_TIME);

      // then
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<Instant, Weather>> existingBySlotCaptor =
          ArgumentCaptor.forClass(Map.class);
      verify(weatherWriter).saveSlots(eq(weatherGrid), eq(BASE_TIME.toInstant()),
          eq(List.of(todaySlot)), existingBySlotCaptor.capture());
      assertThat(existingBySlotCaptor.getValue())
          .containsEntry(Instant.parse("2026-07-26T00:00:00Z"), yesterdaySlot);
    }

    @Test
    @DisplayName("호출_로그에_격자_좌표_등_위치_정보를_남기지_않는다")
    void 호출_로그에_격자_좌표_등_위치_정보를_남기지_않는다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(eq(weatherGrid),
          any())).willReturn(List.of());
      given(kmaForecastFetcher.fetchSlots(GRID, BASE_TIME)).willReturn(List.of());
      given(weatherWriter.saveSlots(any(), any(), any(), any())).willReturn(List.of());

      // when
      weatherRefresher.refreshSlots(weatherGrid, GRID, BASE_TIME);

      // then - conventions.md 11번: 위치 정보(위·경도 등)는 로그에 그대로 남기지 않는다
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(message -> message.contains("nx=") || message.contains("ny="));
    }
  }

  @Nested
  @DisplayName("비동기 재조회(single-flight)")
  class RefreshSlotsAsync {

    private final Executor directExecutor = Runnable::run;

    @Test
    @DisplayName("같은_격자_같은_baseTime_동시_호출은_SingleFlightRegistry를_한_번만_호출한다")
    void 같은_격자_같은_baseTime_동시_호출은_SingleFlightRegistry를_한_번만_호출한다() throws Exception {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      List<Weather> dbSlots = List.of();
      CountDownLatch executeStarted = new CountDownLatch(1);
      CountDownLatch releaseExecute = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      given(singleFlightRegistry.execute(anyString(), any(), any(), any())).willAnswer(
          invocation -> {
            executeStarted.countDown();
            return CompletableFuture.supplyAsync(() -> {
              try {
                releaseExecute.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.<Weather>of();
            }, pool);
          });

      try {
        // when - 두 스레드가 동시에 같은 키로 refreshSlotsAsync 호출
        Future<?> first = pool.submit(() ->
            weatherRefresher.refreshSlotsAsync(weatherGrid, GRID, BASE_TIME, dbSlots, pool));
        assertThat(executeStarted.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<List<Weather>> second =
            weatherRefresher.refreshSlotsAsync(weatherGrid, GRID, BASE_TIME, dbSlots, pool);
        releaseExecute.countDown();
        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);

        // then
        verify(singleFlightRegistry, times(1)).execute(anyString(), any(), any(), any());
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    @DisplayName("로컬_in_flight에_없으면_SingleFlightRegistry로_위임한다")
    void 로컬_in_flight에_없으면_SingleFlightRegistry로_위임한다() {
      // given - lock key에 식별자가 실제로 들어가는지 검증할 수 있도록 id를 직접 채워서
      // 기대 키를 별도로 구성한다
      WeatherGrid weatherGrid = ENTITY_FIXTURE_MONKEY.giveMeBuilder(WeatherGrid.class)
          .set("x", 60)
          .set("y", 127)
          .sample();
      UUID weatherGridId = UUID.randomUUID();
      ReflectionTestUtils.setField(weatherGrid, "id", weatherGridId);
      List<Weather> dbSlots = List.of();
      given(singleFlightRegistry.execute(anyString(), any(), any(), any()))
          .willReturn(CompletableFuture.completedFuture(List.of()));

      // when
      weatherRefresher.refreshSlotsAsync(weatherGrid, GRID, BASE_TIME, dbSlots, directExecutor);

      // then - 락 키에 baseTime까지 포함(로컬 InFlightKey와 동일 granularity)
      verify(singleFlightRegistry).execute(
          eq("weather:" + weatherGridId + ":" + BASE_TIME.baseDate() + BASE_TIME.baseTime()),
          any(), eq(directExecutor), any());
    }
  }

}