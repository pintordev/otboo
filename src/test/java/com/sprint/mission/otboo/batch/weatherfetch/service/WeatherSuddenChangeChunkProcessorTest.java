package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.RepresentativeSlotSelector;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
@DisplayName("WeatherSuddenChangeChunkProcessor")
class WeatherSuddenChangeChunkProcessorTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  // KST 자정 = UTC 전날 15시 - today(2026-07-27 KST)의 00:00 KST를 UTC Instant로 정확히 표현
  private static final Instant D0 = Instant.parse("2026-07-26T15:00:00Z");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T01:00:00Z"), KST);

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private RepresentativeSlotSelector representativeSlotSelector;
  @Mock
  private WeatherD1BaselineRepository weatherD1BaselineRepository;
  @Mock
  private WeatherSuddenChangeGridProcessor gridProcessor;

  private WeatherSuddenChangeChunkProcessor processor;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    processor = new WeatherSuddenChangeChunkProcessor(weatherRepository, representativeSlotSelector,
        weatherD1BaselineRepository, gridProcessor, CLOCK);
    logger = (Logger) LoggerFactory.getLogger(WeatherSuddenChangeChunkProcessor.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  private WeatherGrid gridWithId(int x, int y) {
    return ENTITY_FIXTURE_MONKEY.giveMeBuilder(WeatherGrid.class)
        .set("x", x)
        .set("y", y)
        .sample();
  }

  private Weather weatherWithBaseline(WeatherGrid grid, Instant forecastAt,
      double baselineTemperature, double currentTemperature) {
    return Weather.create(grid, D0, forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
        0.0, 65.0, 0.0, currentTemperature, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK,
        baselineTemperature, PrecipitationType.NONE, 0.0, 0.0, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
  }

  private Weather weatherWithNullBaseline(WeatherGrid grid, Instant forecastAt) {
    return Weather.create(grid, D0, forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
        0.0, 65.0, 0.0, 25.0, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK,
        null, PrecipitationType.NONE, 0.0, 0.0, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
  }

  @Nested
  @DisplayName("HandleD0")
  class HandleD0 {

    @Test
    @DisplayName("그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다")
    void 그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);

      // when
      processor.handleD0(List.of(gridA, gridB), baseTime, today);

      // then
      verify(weatherRepository, times(1))
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
              List.of(gridA.getId(), gridB.getId()),
              today.atStartOfDay(KST).toInstant(), today.plusDays(1).atStartOfDay(KST).toInstant());
    }

    @Test
    @DisplayName("target_슬롯을_찾으면_gridProcessor의_evaluateD0_결과를_그대로_합산한다")
    void target_슬롯을_찾으면_gridProcessor의_evaluateD0_결과를_그대로_합산한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), today.atStartOfDay(KST).toInstant(),
          today.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(target));
      given(representativeSlotSelector.select(List.of(target), baseTime.toInstant()))
          .willReturn(Optional.of(target));
      given(gridProcessor.evaluateD0(target)).willReturn(true);

      // when
      int notified = processor.handleD0(List.of(grid), baseTime, today);

      // then
      assertThat(notified).isEqualTo(1);
      verify(gridProcessor).evaluateD0(target);
    }

    @Test
    @DisplayName("gridProcessor가_false를_반환하면_카운트하지_않는다")
    void gridProcessor가_false를_반환하면_카운트하지_않는다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 20.5);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), today.atStartOfDay(KST).toInstant(),
          today.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(target));
      given(representativeSlotSelector.select(List.of(target), baseTime.toInstant()))
          .willReturn(Optional.of(target));
      given(gridProcessor.evaluateD0(target)).willReturn(false);

      // when
      int notified = processor.handleD0(List.of(grid), baseTime, today);

      // then
      assertThat(notified).isZero();
    }

    @Test
    @DisplayName("baseline_컬럼_중_하나라도_null이면_평가없이_건너뛴다")
    void baseline_컬럼_중_하나라도_null이면_평가없이_건너뛴다() {
      // given - baseline_temperature_current가 null인 슬롯(정상 쓰기 경로에서는 발생하지
      // 않지만 방어적으로 처리한다)
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid grid = gridWithId(60, 127);
      Weather incomplete = weatherWithNullBaseline(grid, baseTime.toInstant());
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), today.atStartOfDay(KST).toInstant(),
          today.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(incomplete));
      given(representativeSlotSelector.select(List.of(incomplete), baseTime.toInstant()))
          .willReturn(Optional.of(incomplete));

      // when
      int notified = processor.handleD0(List.of(grid), baseTime, today);

      // then
      assertThat(notified).isZero();
      verify(gridProcessor, never()).evaluateD0(any());
    }

    @Test
    @DisplayName("baseTime_정각_슬롯이_없어도_당일_가장_가까운_슬롯을_평가_대상으로_삼는다")
    void baseTime_정각_슬롯이_없어도_당일_가장_가까운_슬롯을_평가_대상으로_삼는다() {
      // given - 20시 baseTime, 실제로는 21시 슬롯만 존재(기상청 특성상 baseTime 정각 슬롯은 없음)
      BaseTime baseTime = new BaseTime("20260727", "2000");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid grid = gridWithId(60, 127);
      Instant slotAt21 = today.atTime(21, 0).atZone(KST).toInstant();
      Weather target = weatherWithBaseline(grid, slotAt21, 20.0, 25.0);
      Instant from = today.atStartOfDay(KST).toInstant();
      Instant to = today.plusDays(1).atStartOfDay(KST).toInstant();
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), from, to)).willReturn(List.of(target));
      given(representativeSlotSelector.select(List.of(target), baseTime.toInstant()))
          .willReturn(Optional.of(target));
      given(gridProcessor.evaluateD0(target)).willReturn(true);

      // when
      int notified = processor.handleD0(List.of(grid), baseTime, today);

      // then
      assertThat(notified).isEqualTo(1);
    }

    @Test
    @DisplayName("한_격자의_D0_평가가_예외로_실패해도_나머지_격자는_계속_처리된다")
    void 한_격자의_D0_평가가_예외로_실패해도_나머지_격자는_계속_처리된다() {
      // given - REQUIRES_NEW로 격자별 트랜잭션이 분리돼 있어, 한 격자의 DB 오류가 같은
      // 청크의 나머지 격자까지 abort시키지 않아야 한다(#283 CodeRabbit 리뷰)
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);
      Weather targetA = weatherWithBaseline(gridA, baseTime.toInstant(), 20.0, 25.0);
      Weather targetB = weatherWithBaseline(gridB, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(gridA.getId(), gridB.getId()), today.atStartOfDay(KST).toInstant(),
          today.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(targetA, targetB));
      given(representativeSlotSelector.select(List.of(targetA), baseTime.toInstant()))
          .willReturn(Optional.of(targetA));
      given(representativeSlotSelector.select(List.of(targetB), baseTime.toInstant()))
          .willReturn(Optional.of(targetB));
      given(gridProcessor.evaluateD0(targetA)).willThrow(new RuntimeException("D0 평가 실패"));
      given(gridProcessor.evaluateD0(targetB)).willReturn(true);

      // when
      int notified = processor.handleD0(List.of(gridA, gridB), baseTime, today);

      // then - gridA는 실패로 카운트되지 않고, gridB는 정상 처리된다
      assertThat(notified).isEqualTo(1);
      verify(gridProcessor).evaluateD0(targetA);
      verify(gridProcessor).evaluateD0(targetB);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.contains("D0 평가 실패"));
    }
  }

  @Nested
  @DisplayName("HandleD1")
  class HandleD1 {

    @Test
    @DisplayName("기존_baseline이_없는_그리드는_D2_슬롯을_일괄_저장하고_슬롯이_없는_그리드는_경고만_남긴다")
    void 기존_baseline이_없는_그리드는_D2_슬롯을_일괄_저장하고_슬롯이_없는_그리드는_경고만_남긴다() {
      // given
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128); // 슬롯 없음(빈 스냅샷 케이스)
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Instant to = d2Date.plusDays(1).atStartOfDay(KST).toInstant();
      Weather slotA = weatherWithBaseline(gridA, hour0, 20.0, 20.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(gridA.getId(), gridB.getId()), hour0, to)).willReturn(List.of(slotA));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(gridA.getId(), gridB.getId()), d2Date)).willReturn(List.of());

      // when
      processor.captureD2Snapshot(List.of(gridA, gridB), d2Date);

      // then - gridB(빈 스냅샷)는 저장 대상에서 빠지고 gridA만 저장된다
      ArgumentCaptor<List<WeatherD1Baseline>> captor = ArgumentCaptor.forClass(List.class);
      verify(weatherD1BaselineRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0).getWeatherGrid()).isEqualTo(gridA);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.contains("슬롯이 없어 D2 스냅샷 캡처를 건너뜀"));
    }

    @Test
    @DisplayName("기존_baseline이_있는_그리드는_hourly_snapshot만_갱신하고_다시_저장하지_않는다")
    void 기존_baseline이_있는_그리드는_hourly_snapshot만_갱신하고_다시_저장하지_않는다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Weather slot0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), hour0, d2Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(slot0));
      WeatherD1Baseline existing = WeatherD1Baseline.create(grid, d2Date, Map.of(),
          Instant.parse("2026-07-26T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d2Date)).willReturn(List.of(existing));

      // when
      processor.captureD2Snapshot(List.of(grid), d2Date);

      // then
      assertThat(existing.getHourlySnapshot())
          .containsExactlyEntriesOf(Map.of(hour0, WeatherChangeSnapshot.currentOf(slot0)));
      assertThat(existing.getCapturedAt()).isEqualTo(CLOCK.instant());
      verify(weatherD1BaselineRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("baseline이_전혀_없으면_비교_쿼리_없이_경고_로그_1줄만_남기고_0을_반환한다")
    void baseline이_전혀_없으면_비교_쿼리_없이_경고_로그_1줄만_남기고_0을_반환한다() {
      // given - 청크에 그리드가 여러 개여도 원인은 하나(어제 20시 캡처 누락)이므로 로그도
      // 집계된 1줄이어야 한다(그리드 수만큼 반복되면 안 됨)
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(gridA.getId(), gridB.getId()), d1Date)).willReturn(List.of());

      // when
      int notified = processor.compareD1AndNotify(List.of(gridA, gridB), d1Date);

      // then
      assertThat(notified).isZero();
      verify(weatherRepository, never())
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(any(),
              any(), any());
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .filteredOn(message -> message.contains("D1 baseline 스냅샷 없음"))
          .hasSize(1);
    }

    @Test
    @DisplayName("baseline과_current를_시각_순서대로_짝지어_gridProcessor의_evaluateD1에_그리드당_1회_전달한다")
    void baseline과_current를_시각_순서대로_짝지어_gridProcessor의_evaluateD1에_그리드당_1회_전달한다() {
      // given - 하루 두 시각이 모두 baseline을 갖는 경우
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      Instant hour0 = d1Date.atStartOfDay(KST).toInstant();
      Instant hour3 = hour0.plusSeconds(3 * 3600);
      WeatherChangeSnapshot baselineHour0 =
          new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot baselineHour3 =
          new WeatherChangeSnapshot(18.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherD1Baseline baselineRow = WeatherD1Baseline.create(grid, d1Date,
          Map.of(hour0, baselineHour0, hour3, baselineHour3),
          Instant.parse("2026-07-27T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date)).willReturn(List.of(baselineRow));

      Weather currentHour0 = weatherWithBaseline(grid, hour0, 20.0, 25.0);
      Weather currentHour3 = weatherWithBaseline(grid, hour3, 18.0, 13.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentHour0, currentHour3));

      given(gridProcessor.evaluateD1(grid, List.of(baselineHour0, baselineHour3),
          List.of(WeatherChangeSnapshot.currentOf(currentHour0),
              WeatherChangeSnapshot.currentOf(currentHour3))))
          .willReturn(true);

      // when
      int notified = processor.compareD1AndNotify(List.of(grid), d1Date);

      // then - 시각이 여러 개여도 evaluateD1은 그리드당 1회다
      assertThat(notified).isEqualTo(1);
      verify(gridProcessor, times(1)).evaluateD1(any(), any(), any());
    }

    @Test
    @DisplayName("어제는_없었던_슬롯은_evaluateD1_대상에서_제외된다")
    void 어제는_없었던_슬롯은_evaluateD1_대상에서_제외된다() {
      // given - hour3는 어제(baseline) 캡처에 없던 슬롯(경계 케이스)
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      Instant hour0 = d1Date.atStartOfDay(KST).toInstant();
      Instant hour3 = hour0.plusSeconds(3 * 3600);
      WeatherChangeSnapshot baselineHour0 =
          new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherD1Baseline baselineRow = WeatherD1Baseline.create(grid, d1Date,
          Map.of(hour0, baselineHour0), Instant.parse("2026-07-27T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date)).willReturn(List.of(baselineRow));

      Weather currentHour0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      Weather currentHour3 = weatherWithBaseline(grid, hour3, 18.0, 18.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentHour0, currentHour3));

      // when
      processor.compareD1AndNotify(List.of(grid), d1Date);

      // then - hour3은 baseline이 없어 evaluateD1로 넘어가는 리스트에서 빠진다(길이 1)
      verify(gridProcessor).evaluateD1(grid, List.of(baselineHour0),
          List.of(WeatherChangeSnapshot.currentOf(currentHour0)));
    }

    @Test
    @DisplayName("여러_그리드의_알림_건수를_합산해서_반환한다")
    void 여러_그리드의_알림_건수를_합산해서_반환한다() {
      // given
      WeatherGrid notifiedGrid = gridWithId(60, 127);
      WeatherGrid quietGrid = gridWithId(61, 128);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      Instant hour0 = d1Date.atStartOfDay(KST).toInstant();
      WeatherChangeSnapshot baselineHour0 =
          new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherD1Baseline baselineRow = WeatherD1Baseline.create(notifiedGrid, d1Date,
          Map.of(hour0, baselineHour0), Instant.parse("2026-07-27T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(notifiedGrid.getId(), quietGrid.getId()), d1Date))
          .willReturn(List.of(baselineRow));

      Weather currentHour0 = weatherWithBaseline(notifiedGrid, hour0, 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(notifiedGrid.getId(), quietGrid.getId()), hour0,
          d1Date.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(currentHour0));
      given(gridProcessor.evaluateD1(notifiedGrid, List.of(baselineHour0),
          List.of(WeatherChangeSnapshot.currentOf(currentHour0))))
          .willReturn(true);

      // when
      int notified = processor.compareD1AndNotify(List.of(notifiedGrid, quietGrid), d1Date);

      // then
      assertThat(notified).isEqualTo(1);
    }

    @Test
    @DisplayName("한_격자의_D1_평가가_예외로_실패해도_나머지_격자는_계속_처리된다")
    void 한_격자의_D1_평가가_예외로_실패해도_나머지_격자는_계속_처리된다() {
      // given
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      Instant hour0 = d1Date.atStartOfDay(KST).toInstant();
      WeatherChangeSnapshot baselineHour0 =
          new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherD1Baseline baselineRowA = WeatherD1Baseline.create(gridA, d1Date,
          Map.of(hour0, baselineHour0), Instant.parse("2026-07-27T11:10:00Z"));
      WeatherD1Baseline baselineRowB = WeatherD1Baseline.create(gridB, d1Date,
          Map.of(hour0, baselineHour0), Instant.parse("2026-07-27T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(gridA.getId(), gridB.getId()), d1Date))
          .willReturn(List.of(baselineRowA, baselineRowB));

      Weather currentA = weatherWithBaseline(gridA, hour0, 20.0, 25.0);
      Weather currentB = weatherWithBaseline(gridB, hour0, 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(gridA.getId(), gridB.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentA, currentB));
      given(gridProcessor.evaluateD1(gridA, List.of(baselineHour0),
          List.of(WeatherChangeSnapshot.currentOf(currentA))))
          .willThrow(new RuntimeException("D1 평가 실패"));
      given(gridProcessor.evaluateD1(gridB, List.of(baselineHour0),
          List.of(WeatherChangeSnapshot.currentOf(currentB))))
          .willReturn(true);

      // when
      int notified = processor.compareD1AndNotify(List.of(gridA, gridB), d1Date);

      // then - gridA는 실패로 카운트되지 않고, gridB는 정상 처리된다
      assertThat(notified).isEqualTo(1);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.contains("D1 평가 실패"));
    }

    @Test
    @DisplayName("오늘_기준으로_D2는_모레_D1은_내일_날짜로_처리한다")
    void 오늘_기준으로_D2는_모레_D1은_내일_날짜로_처리한다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate today = LocalDate.parse("2026-07-27");
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d2Date)).willReturn(List.of());
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date)).willReturn(List.of());
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          any(), any(), any())).willReturn(List.of());

      // when
      processor.handleD1(List.of(grid), today);

      // then
      verify(weatherRepository).findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), d2Date.atStartOfDay(KST).toInstant(),
          d2Date.plusDays(1).atStartOfDay(KST).toInstant());
      verify(weatherD1BaselineRepository).findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date);
    }
  }

  @Nested
  @DisplayName("Process")
  class Process {

    @Test
    @DisplayName("shouldHandleD0와_shouldHandleD1이_모두_false면_아무것도_조회하지_않고_0을_반환한다")
    void shouldHandleD0와_shouldHandleD1이_모두_false면_아무것도_조회하지_않고_0을_반환한다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");

      // when
      WeatherSuddenChangeChunkProcessor.ChunkResult result =
          processor.process(List.of(grid), baseTime, today, false, false);

      // then
      assertThat(result.d0Notified()).isZero();
      assertThat(result.d1Notified()).isZero();
      verify(weatherRepository, never())
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(any(), any(),
              any());
      verify(weatherD1BaselineRepository, never())
          .findAllByWeatherGridIdInAndTargetDate(any(), any());
    }

    @Test
    @DisplayName("shouldHandleD0와_shouldHandleD1이_모두_true면_각각_평가하고_결과를_합산한다")
    void shouldHandleD0와_shouldHandleD1이_모두_true면_각각_평가하고_결과를_합산한다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      LocalDate today = LocalDate.parse("2026-07-27");
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      LocalDate d2Date = LocalDate.parse("2026-07-29");

      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), today.atStartOfDay(KST).toInstant(),
          today.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(target));
      given(representativeSlotSelector.select(List.of(target), baseTime.toInstant()))
          .willReturn(Optional.of(target));
      given(gridProcessor.evaluateD0(target)).willReturn(true);

      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
          List.of(grid.getId()), d2Date.atStartOfDay(KST).toInstant(),
          d2Date.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of());
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d2Date)).willReturn(List.of());
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date)).willReturn(List.of());

      // when
      WeatherSuddenChangeChunkProcessor.ChunkResult result =
          processor.process(List.of(grid), baseTime, today, true, true);

      // then
      assertThat(result.d0Notified()).isEqualTo(1);
      assertThat(result.d1Notified()).isZero();
    }
  }
}