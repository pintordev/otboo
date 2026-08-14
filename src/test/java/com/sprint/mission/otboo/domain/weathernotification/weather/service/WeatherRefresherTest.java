package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherRefresher = new WeatherRefresher(weatherRepository, kmaForecastFetcher, weatherWriter,
        clock);
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

}