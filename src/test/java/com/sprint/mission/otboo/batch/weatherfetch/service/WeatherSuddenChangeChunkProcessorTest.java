package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator.ChangeResult;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
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
import org.springframework.context.ApplicationEventPublisher;

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
  private ProfileRepository profileRepository;
  @Mock
  private WeatherD1BaselineRepository weatherD1BaselineRepository;
  @Mock
  private WeatherChangeEvaluator weatherChangeEvaluator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private WeatherSuddenChangeChunkProcessor processor;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    processor = new WeatherSuddenChangeChunkProcessor(weatherRepository, profileRepository,
        weatherD1BaselineRepository, weatherChangeEvaluator, eventPublisher, CLOCK);
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

  private Profile profileWithLocation(List<String> locationNames) {
    UUID userId = UUID.randomUUID();
    User user = ENTITY_FIXTURE_MONKEY.giveMeBuilder(User.class).set("id", userId).sample();
    return ENTITY_FIXTURE_MONKEY.giveMeBuilder(Profile.class)
        .set("id", userId)
        .set("user", user)
        .set("location", Location.create(37.5, 127.0, 60, 127, locationNames))
        .sample();
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
        baselineTemperature, PrecipitationType.NONE, 0.0, 0.0);
  }

  private Weather weatherWithNullBaseline(WeatherGrid grid, Instant forecastAt) {
    return Weather.create(grid, D0, forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
        0.0, 65.0, 0.0, 25.0, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK,
        null, PrecipitationType.NONE, 0.0, 0.0);
  }

  @Nested
  @DisplayName("HandleD0")
  class HandleD0 {

    @Test
    @DisplayName("그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다")
    void 그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);

      // when
      processor.handleD0(List.of(gridA, gridB), baseTime);

      // then
      verify(weatherRepository, times(1)).findAllByWeatherGridIdInAndForecastAt(
          List.of(gridA.getId(), gridB.getId()), baseTime.toInstant());
    }

    @Test
    @DisplayName("baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다")
    void baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when
      int notified = processor.handleD0(List.of(grid), baseTime);

      // then
      assertThat(notified).isEqualTo(1);
      verify(eventPublisher).publishEvent(any(NotificationRequestedEvent.class));
      verify(weatherRepository).updateBaseline(target.getId(), 25.0, PrecipitationType.NONE, 0.0,
          0.0);
    }

    @Test
    @DisplayName("임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다")
    void 임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다() {
      // given - weatherChangeEvaluator는 mock이라 별도 stub 없으면 Optional.empty()를
      // 기본 반환한다
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 20.5);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));

      // when
      int notified = processor.handleD0(List.of(grid), baseTime);

      // then
      assertThat(notified).isZero();
      verify(eventPublisher, never()).publishEvent(any());
      verify(weatherRepository, never()).updateBaseline(any(), anyDouble(), any(), anyDouble(),
          anyDouble());
    }

    @Test
    @DisplayName("baseline_컬럼_중_하나라도_null이면_평가없이_건너뛴다")
    void baseline_컬럼_중_하나라도_null이면_평가없이_건너뛴다() {
      // given - baseline_temperature_current가 null인 슬롯(정상 쓰기 경로에서는 발생하지
      // 않지만 방어적으로 처리한다)
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather incomplete = weatherWithNullBaseline(grid, baseTime.toInstant());
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(incomplete));

      // when
      int notified = processor.handleD0(List.of(grid), baseTime);

      // then
      assertThat(notified).isZero();
      verify(weatherChangeEvaluator, never()).evaluate(any(), any());
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("Publish")
  class Publish {

    @Test
    @DisplayName("locationNames가_null이어도_예외_없이_발행한다")
    void locationNames가_null이어도_예외_없이_발행한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(null);
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when
      assertThatCode(() -> processor.handleD0(List.of(grid), baseTime))
          .doesNotThrowAnyException();

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().content()).isEqualTo("기온이 5.0도 올랐어요.");
    }

    @Test
    @DisplayName("같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다")
    void 같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile gangnamProfile = profileWithLocation(List.of("서울특별시", "강남구"));
      Profile seochoProfile = profileWithLocation(List.of("서울특별시", "서초구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(gangnamProfile, seochoProfile));

      // when
      processor.handleD0(List.of(grid), baseTime);

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher, times(2)).publishEvent(captor.capture());
      assertThat(captor.getAllValues())
          .anySatisfy(event -> {
            assertThat(event.receiverIds()).containsExactly(gangnamProfile.getId());
            assertThat(event.content()).startsWith("강남구 ");
          })
          .anySatisfy(event -> {
            assertThat(event.receiverIds()).containsExactly(seochoProfile.getId());
            assertThat(event.content()).startsWith("서초구 ");
          });
    }

    @Test
    @DisplayName("수신자가_없어도_baseline은_리셋하고_이벤트는_발행하지_않는다")
    void 수신자가_없어도_baseline은_리셋하고_이벤트는_발행하지_않는다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      given(profileRepository.findByLocation(grid.getX(), grid.getY())).willReturn(List.of());

      // when
      int notified = processor.handleD0(List.of(grid), baseTime);

      // then
      assertThat(notified).isZero();
      verify(eventPublisher, never()).publishEvent(any(NotificationRequestedEvent.class));
      verify(weatherRepository).updateBaseline(target.getId(), 25.0, PrecipitationType.NONE, 0.0,
          0.0);
    }
  }

  @Nested
  @DisplayName("HandleD1")
  class HandleD1 {

    @Test
    @DisplayName("기존_baseline이_없는_그리드는_D2_슬롯을_일괄_저장한다")
    void 기존_baseline이_없는_그리드는_D2_슬롯을_일괄_저장한다() {
      // given
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Instant to = d2Date.plusDays(1).atStartOfDay(KST).toInstant();
      Weather slotA = weatherWithBaseline(gridA, hour0, 20.0, 20.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          List.of(gridA.getId(), gridB.getId()), hour0, to)).willReturn(List.of(slotA));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(gridA.getId(), gridB.getId()), d2Date)).willReturn(List.of());

      // when
      processor.captureD2Snapshot(List.of(gridA, gridB), d2Date);

      // then
      ArgumentCaptor<List<WeatherD1Baseline>> captor = ArgumentCaptor.forClass(List.class);
      verify(weatherD1BaselineRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).hasSize(2);
      assertThat(captor.getValue())
          .anySatisfy(baseline -> {
            assertThat(baseline.getWeatherGrid()).isEqualTo(gridA);
            assertThat(baseline.getHourlySnapshot())
                .containsExactlyEntriesOf(Map.of(hour0, WeatherChangeSnapshot.currentOf(slotA)));
          })
          .anySatisfy(baseline -> {
            assertThat(baseline.getWeatherGrid()).isEqualTo(gridB);
            assertThat(baseline.getHourlySnapshot()).isEmpty();
          });
    }

    @Test
    @DisplayName("기존_baseline이_있는_그리드는_hourly_snapshot만_갱신하고_다시_저장하지_않는다")
    void 기존_baseline이_있는_그리드는_hourly_snapshot만_갱신하고_다시_저장하지_않는다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Weather slot0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
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
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(any(),
              any(), any());
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .filteredOn(message -> message.contains("D1 baseline 스냅샷 없음"))
          .hasSize(1);
    }

    @Test
    @DisplayName("baseline과_다른_시각만_개별적으로_비교해_발행한다")
    void baseline과_다른_시각만_개별적으로_비교해_발행한다() {
      // given
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
      Weather currentHour3 = weatherWithBaseline(grid, hour3, 18.0, 18.5);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          List.of(grid.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentHour0, currentHour3));

      given(weatherChangeEvaluator.evaluate(baselineHour0,
          WeatherChangeSnapshot.currentOf(currentHour0)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      given(weatherChangeEvaluator.evaluate(baselineHour3,
          WeatherChangeSnapshot.currentOf(currentHour3)))
          .willReturn(Optional.empty());
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when
      int notified = processor.compareD1AndNotify(List.of(grid), d1Date);

      // then
      assertThat(notified).isEqualTo(1);
      verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
    }

    @Test
    @DisplayName("한_그리드_안에서_여러_시각이_동시에_임계값을_넘어도_발행은_그리드당_1회다")
    void 한_그리드_안에서_여러_시각이_동시에_임계값을_넘어도_발행은_그리드당_1회다() {
      // given
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
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          List.of(grid.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentHour0, currentHour3));

      given(weatherChangeEvaluator.evaluate(baselineHour0,
          WeatherChangeSnapshot.currentOf(currentHour0)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      given(weatherChangeEvaluator.evaluate(baselineHour3,
          WeatherChangeSnapshot.currentOf(currentHour3)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 내렸어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when
      int notified = processor.compareD1AndNotify(List.of(grid), d1Date);

      // then - 시각 2개가 모두 임계값을 넘어도 publish/profileRepository 조회는 그리드당 1회다
      assertThat(notified).isEqualTo(1);
      verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestedEvent.class));
      verify(profileRepository, times(1)).findByLocation(grid.getX(), grid.getY());
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().content())
          .contains("기온이 5.0도 올랐어요.")
          .contains("기온이 5.0도 내렸어요.");
    }

    @Test
    @DisplayName("어제는_없었던_슬롯은_비교를_건너뛴다")
    void 어제는_없었던_슬롯은_비교를_건너뛴다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d1Date = LocalDate.parse("2026-07-28");
      Instant hour0 = d1Date.atStartOfDay(KST).toInstant();
      Instant hour3 = hour0.plusSeconds(3 * 3600);
      WeatherD1Baseline baselineRow = WeatherD1Baseline.create(grid, d1Date,
          Map.of(hour0, new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0)),
          Instant.parse("2026-07-27T11:10:00Z"));
      given(weatherD1BaselineRepository.findAllByWeatherGridIdInAndTargetDate(
          List.of(grid.getId()), d1Date)).willReturn(List.of(baselineRow));

      Weather currentHour0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      Weather currentHour3 = weatherWithBaseline(grid, hour3, 18.0, 18.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          List.of(grid.getId()), hour0, d1Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(currentHour0, currentHour3));

      // when
      processor.compareD1AndNotify(List.of(grid), d1Date);

      // then
      verify(weatherChangeEvaluator, never()).evaluate(any(),
          eq(WeatherChangeSnapshot.currentOf(currentHour3)));
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
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          List.of(notifiedGrid.getId(), quietGrid.getId()), hour0,
          d1Date.plusDays(1).atStartOfDay(KST).toInstant())).willReturn(List.of(currentHour0));
      given(weatherChangeEvaluator.evaluate(baselineHour0,
          WeatherChangeSnapshot.currentOf(currentHour0)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(notifiedGrid.getX(), notifiedGrid.getY()))
          .willReturn(List.of(profile));

      // when
      int notified = processor.compareD1AndNotify(List.of(notifiedGrid, quietGrid), d1Date);

      // then
      assertThat(notified).isEqualTo(1);
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
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
          any(), any(), any())).willReturn(List.of());

      // when
      processor.handleD1(List.of(grid), today);

      // then
      verify(weatherRepository).findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
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
      verify(weatherRepository, never()).findAllByWeatherGridIdInAndForecastAt(any(), any());
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
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      given(weatherRepository.findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
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