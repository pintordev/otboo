package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherSuddenChangeNotifier")
class WeatherSuddenChangeNotifierTest {

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

  private WeatherSuddenChangeNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new WeatherSuddenChangeNotifier(weatherRepository, profileRepository,
        weatherD1BaselineRepository, weatherChangeEvaluator, eventPublisher, CLOCK);
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

  @Nested
  @DisplayName("HandleD0")
  class HandleD0 {

    @Test
    @DisplayName("그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다")
    void 그리드_수와_무관하게_대상_슬롯을_쿼리_1번으로_조회한다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid gridA = gridWithId(60, 127);
      WeatherGrid gridB = gridWithId(61, 128);

      notifier.handleD0(List.of(gridA, gridB), baseTime);

      verify(weatherRepository, times(1)).findAllByWeatherGridIdInAndForecastAt(
          List.of(gridA.getId(), gridB.getId()), baseTime.toInstant());
    }

    @Test
    @DisplayName("baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다")
    void baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다() {
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

      int notified = notifier.handleD0(List.of(grid), baseTime);

      assertThat(notified).isEqualTo(1);
      verify(eventPublisher).publishEvent(any(NotificationRequestedEvent.class));
      verify(weatherRepository).updateBaseline(target.getId(), 25.0, PrecipitationType.NONE, 0.0,
          0.0);
    }

    @Test
    @DisplayName("임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다")
    void 임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 20.5);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      // weatherChangeEvaluator는 mock이라 별도 stub 없으면 Optional.empty()를 기본 반환한다

      int notified = notifier.handleD0(List.of(grid), baseTime);

      assertThat(notified).isZero();
      verify(eventPublisher, never()).publishEvent(any());
      verify(weatherRepository, never()).updateBaseline(any(), anyDouble(), any(), anyDouble(),
          anyDouble());
    }
  }

  @Nested
  @DisplayName("Publish")
  class Publish {

    @Test
    @DisplayName("locationNames가_null이어도_예외_없이_발행한다")
    void locationNames가_null이어도_예외_없이_발행한다() {
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

      assertThatCode(() -> notifier.handleD0(List.of(grid), baseTime))
          .doesNotThrowAnyException();
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().content()).isEqualTo("기온이 5.0도 올랐어요.");
    }

    @Test
    @DisplayName("같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다")
    void 같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다() {
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

      notifier.handleD0(List.of(grid), baseTime);

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
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      given(profileRepository.findByLocation(grid.getX(), grid.getY())).willReturn(List.of());

      int notified = notifier.handleD0(List.of(grid), baseTime);

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
    @DisplayName("기존_baseline이_없으면_D2_24시간_슬롯을_새로_저장한다")
    void 기존_baseline이_없으면_D2_24시간_슬롯을_새로_저장한다() {
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Instant hour3 = hour0.plusSeconds(3 * 3600);
      Weather slot0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      Weather slot3 = weatherWithBaseline(grid, hour3, 18.0, 18.0);
      given(weatherRepository
          .findAllByWeatherGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, hour0,
              d2Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(slot0, slot3));
      given(weatherD1BaselineRepository.findByWeatherGridAndTargetDate(grid, d2Date))
          .willReturn(Optional.empty());

      notifier.captureD2Snapshot(grid, d2Date);

      ArgumentCaptor<WeatherD1Baseline> captor = ArgumentCaptor.forClass(WeatherD1Baseline.class);
      verify(weatherD1BaselineRepository).save(captor.capture());
      WeatherD1Baseline saved = captor.getValue();
      assertThat(saved.getWeatherGrid()).isEqualTo(grid);
      assertThat(saved.getTargetDate()).isEqualTo(d2Date);
      assertThat(saved.getHourlySnapshot()).containsExactlyInAnyOrderEntriesOf(Map.of(
          hour0, WeatherChangeSnapshot.currentOf(slot0),
          hour3, WeatherChangeSnapshot.currentOf(slot3)));
      assertThat(saved.getCapturedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("기존_baseline이_있으면_hourly_snapshot을_갱신하고_다시_저장하지_않는다")
    void 기존_baseline이_있으면_hourly_snapshot을_갱신하고_다시_저장하지_않는다() {
      WeatherGrid grid = gridWithId(60, 127);
      LocalDate d2Date = LocalDate.parse("2026-07-29");
      Instant hour0 = d2Date.atStartOfDay(KST).toInstant();
      Weather slot0 = weatherWithBaseline(grid, hour0, 20.0, 20.0);
      given(weatherRepository
          .findAllByWeatherGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, hour0,
              d2Date.plusDays(1).atStartOfDay(KST).toInstant()))
          .willReturn(List.of(slot0));
      WeatherD1Baseline existing = WeatherD1Baseline.create(grid, d2Date, Map.of(),
          Instant.parse("2026-07-26T11:10:00Z"));
      given(weatherD1BaselineRepository.findByWeatherGridAndTargetDate(grid, d2Date))
          .willReturn(Optional.of(existing));

      notifier.captureD2Snapshot(grid, d2Date);

      assertThat(existing.getHourlySnapshot())
          .containsExactlyEntriesOf(Map.of(hour0, WeatherChangeSnapshot.currentOf(slot0)));
      assertThat(existing.getCapturedAt()).isEqualTo(CLOCK.instant());
      verify(weatherD1BaselineRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("DetectAndNotify")
  class DetectAndNotify {

    @Test
    @DisplayName("격자_업데이트가_없으면_D0를_평가하지_않는다")
    void 격자_업데이트가_없으면_D0를_평가하지_않는다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of());

      notifier.detectAndNotify(baseTime);

      verify(weatherRepository, never()).findAllByWeatherGridIdInAndForecastAt(any(), any());
    }

    @Test
    @DisplayName("baseTime이_2300이면_D0를_평가하지_않는다")
    void baseTime이_2300이면_D0를_평가하지_않는다() {
      BaseTime baseTime = new BaseTime("20260727", "2300");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      notifier.detectAndNotify(baseTime);

      verify(weatherRepository, never()).findAllByWeatherGridIdInAndForecastAt(any(), any());
    }

    @Test
    @DisplayName("baseTime이_2300이_아니면_D0를_평가한다")
    void baseTime이_2300이_아니면_D0를_평가한다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      notifier.detectAndNotify(baseTime);

      verify(weatherRepository).findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant());
    }

    @Test
    @DisplayName("baseline_대비_변경이_감지되면_detectAndNotify를_통해_알림이_발행된다")
    void baseline_대비_변경이_감지되면_detectAndNotify를_통해_알림이_발행된다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, baseTime.toInstant(), 20.0, 25.0);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(weatherRepository.findAllByWeatherGridIdInAndForecastAt(List.of(grid.getId()),
          baseTime.toInstant())).willReturn(List.of(target));
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      notifier.detectAndNotify(baseTime);

      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().content()).startsWith("강남구 ");
      verify(weatherRepository).updateBaseline(target.getId(), 25.0, PrecipitationType.NONE, 0.0,
          0.0);
    }
  }
}