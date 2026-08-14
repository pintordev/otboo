package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.WeatherChangeNotificationLogRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator.ChangeResult;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T01:00:00Z"), KST);
  // KST 자정 = UTC 전날 15시 - today(2026-07-27 KST)의 00:00 KST를 UTC Instant로 정확히 표현
  private static final Instant D0 = Instant.parse("2026-07-26T15:00:00Z");
  private static final Instant D1 = Instant.parse("2026-07-27T15:00:00Z");

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private ProfileRepository profileRepository;
  @Mock
  private WeatherChangeNotificationLogRepository notificationLogRepository;
  @Mock
  private WeatherChangeEvaluator weatherChangeEvaluator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private WeatherSuddenChangeNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new WeatherSuddenChangeNotifier(weatherRepository, profileRepository,
        notificationLogRepository, weatherChangeEvaluator, eventPublisher, CLOCK);
  }

  private WeatherGrid gridOf(int x, int y) {
    return WeatherGrid.create(x, y);
  }

  private Weather weatherOf(WeatherGrid grid, Instant forecastAt, Instant forecastedAt,
      double temperature) {
    return Weather.create(grid, forecastedAt, forecastAt, SkyStatus.CLEAR,
        PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, temperature, 0.0, 25.0, 31.0, 2.5,
        WindStrength.WEAK, null, null, null, null);
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

  @Nested
  @DisplayName("DetectAndNotify")
  class DetectAndNotify {

    @Test
    @DisplayName("임계값을_넘는_격자만_이벤트를_발행하고_content는_지역명으로_시작한다")
    void 임계값을_넘는_격자만_이벤트를_발행하고_content는_지역명으로_시작한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid changedGrid = gridOf(60, 127);
      WeatherGrid unchangedGrid = gridOf(61, 128);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant()))
          .willReturn(List.of(changedGrid, unchangedGrid));

      Weather changedPrevious = weatherOf(changedGrid, D0, Instant.parse("2026-07-27T02:10:00Z"),
          20.0);
      Weather changedLatest = weatherOf(changedGrid, D0, Instant.parse("2026-07-27T08:10:00Z"),
          25.0);
      given(weatherRepository.findRecentTwoRevisions(changedGrid, List.of(D0)))
          .willReturn(List.of(changedPrevious, changedLatest));

      Weather unchangedPrevious = weatherOf(unchangedGrid, D0,
          Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather unchangedLatest = weatherOf(unchangedGrid, D0, Instant.parse("2026-07-27T08:10:00Z"),
          20.5);
      given(weatherRepository.findRecentTwoRevisions(unchangedGrid, List.of(D0)))
          .willReturn(List.of(unchangedPrevious, unchangedLatest));

      given(notificationLogRepository.findByWeatherGridAndForecastAt(any(), any()))
          .willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(changedPrevious, changedLatest))
          .willReturn(Optional.of(new ChangeResult(changedGrid, D0,
              changedLatest.getForecastedAt(), List.of("기온이 5.0도 올랐어요."))));
      given(weatherChangeEvaluator.evaluate(unchangedPrevious, unchangedLatest))
          .willReturn(Optional.empty());

      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(changedGrid.getX(), changedGrid.getY()))
          .willReturn(List.of(profile));

      // when
      notifier.detectAndNotify(baseTime);

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      NotificationRequestedEvent event = captor.getValue();
      assertThat(event.receiverIds()).containsExactly(profile.getId());
      assertThat(event.title()).isEqualTo("날씨 급변");
      assertThat(event.content()).startsWith("강남구 ");
      assertThat(event.level()).isEqualTo(NotificationLevel.WARNING);
    }

    @Test
    @DisplayName("locationNames가_null이어도_예외_없이_발행한다")
    void locationNames가_null이어도_예외_없이_발행한다() {
      // given - LocationRequest.locationNames엔 @NotNull이 없어 null로 등록될 수 있다
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(previous, latest))
          .willReturn(Optional.of(
              new ChangeResult(grid, D0, latest.getForecastedAt(), List.of("기온이 5.0도 올랐어요."))));

      Profile profile = profileWithLocation(null);
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when & then
      assertThatCode(() -> notifier.detectAndNotify(baseTime)).doesNotThrowAnyException();
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().content()).isEqualTo("기온이 5.0도 올랐어요.");
    }

    @Test
    @DisplayName("같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다")
    void 같은_격자여도_locationNames가_다르면_그룹별로_별도_이벤트를_발행한다() {
      // given - 같은 5km 격자 안에도 구 경계 근처면 프로필마다 등록된 locationNames가 다를 수 있다
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(previous, latest))
          .willReturn(Optional.of(
              new ChangeResult(grid, D0, latest.getForecastedAt(), List.of("기온이 5.0도 올랐어요."))));

      Profile gangnamProfile = profileWithLocation(List.of("서울특별시", "강남구"));
      Profile seochoProfile = profileWithLocation(List.of("서울특별시", "서초구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(gangnamProfile, seochoProfile));

      // when
      notifier.detectAndNotify(baseTime);

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
      // 알림 로그는 그룹 수와 무관하게 (격자, forecastAt) 단위로 1건만 기록한다
      verify(notificationLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("수신자가_없으면_이벤트를_발행하지_않는다")
    void 수신자가_없으면_이벤트를_발행하지_않는다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant()))
          .willReturn(List.of(grid));

      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(previous, latest))
          .willReturn(Optional.of(
              new ChangeResult(grid, D0, latest.getForecastedAt(), List.of("기온이 5.0도 올랐어요."))));
      given(profileRepository.findByLocation(grid.getX(), grid.getY())).willReturn(List.of());

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(eventPublisher, never()).publishEvent(any(NotificationRequestedEvent.class));
      verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("리비전_순서가_뒤집혀_들어와도_최신과_직전을_올바르게_구분한다")
    void 리비전_순서가_뒤집혀_들어와도_최신과_직전을_올바르게_구분한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      // 최신 리비전이 먼저, 직전 리비전이 나중에 오도록 순서를 뒤집는다
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(latest, previous));
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(any(), any())).willReturn(Optional.empty());

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(weatherChangeEvaluator).evaluate(previous, latest);
    }

    @Test
    @DisplayName("알림_로그가_있으면_직전_리비전이_아니라_로그의_리비전과_비교한다")
    void 알림_로그가_있으면_직전_리비전이_아니라_로그의_리비전과_비교한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Instant loggedForecastedAt = Instant.parse("2026-07-27T05:10:00Z");
      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather logged = weatherOf(grid, D0, loggedForecastedAt, 21.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));

      WeatherChangeNotificationLog log = WeatherChangeNotificationLog.create(grid, D0,
          loggedForecastedAt);
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.of(log));
      given(weatherRepository.findByWeatherGridAndForecastAtAndForecastedAt(grid, D0,
          loggedForecastedAt)).willReturn(Optional.of(logged));
      given(weatherChangeEvaluator.evaluate(any(), any())).willReturn(Optional.empty());

      // when
      notifier.detectAndNotify(baseTime);

      // then - 직전 리비전(previous)이 아니라 로그가 가리키는 리비전(logged)과 비교한다
      verify(weatherChangeEvaluator).evaluate(logged, latest);
    }

    @Test
    @DisplayName("로그_기준_대비_노이즈_수준이면_재발행하지_않는다")
    void 로그_기준_대비_노이즈_수준이면_재발행하지_않는다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Instant loggedForecastedAt = Instant.parse("2026-07-27T05:10:00Z");
      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather logged = weatherOf(grid, D0, loggedForecastedAt, 24.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));

      WeatherChangeNotificationLog log = WeatherChangeNotificationLog.create(grid, D0,
          loggedForecastedAt);
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.of(log));
      given(weatherRepository.findByWeatherGridAndForecastAtAndForecastedAt(grid, D0,
          loggedForecastedAt)).willReturn(Optional.of(logged));
      // logged(24.0) -> latest(25.0)는 임계값 미만이라 감지되지 않음
      given(weatherChangeEvaluator.evaluate(logged, latest)).willReturn(Optional.empty());

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(eventPublisher, never()).publishEvent(any(NotificationRequestedEvent.class));
    }

    @Test
    @DisplayName("로그_기준_대비_추가로_임계값을_넘으면_재발행하고_로그를_갱신한다")
    void 로그_기준_대비_추가로_임계값을_넘으면_재발행하고_로그를_갱신한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Instant loggedForecastedAt = Instant.parse("2026-07-27T05:10:00Z");
      Instant latestForecastedAt = Instant.parse("2026-07-27T08:10:00Z");
      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather logged = weatherOf(grid, D0, loggedForecastedAt, 23.0);
      Weather latest = weatherOf(grid, D0, latestForecastedAt, 30.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));

      WeatherChangeNotificationLog log = WeatherChangeNotificationLog.create(grid, D0,
          loggedForecastedAt);
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.of(log));
      given(weatherRepository.findByWeatherGridAndForecastAtAndForecastedAt(grid, D0,
          loggedForecastedAt)).willReturn(Optional.of(logged));
      given(weatherChangeEvaluator.evaluate(logged, latest))
          .willReturn(Optional.of(
              new ChangeResult(grid, D0, latestForecastedAt, List.of("기온이 7.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY())).willReturn(
          List.of(profile));

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(eventPublisher).publishEvent(any(NotificationRequestedEvent.class));
      assertThat(log.getLastNotifiedForecastedAt()).isEqualTo(latestForecastedAt);
      // baseline 판정과 로그 갱신이 같은 로그 조회를 재사용해야 한다 - 중복 조회 금지
      verify(notificationLogRepository, times(1)).findByWeatherGridAndForecastAt(grid, D0);
    }

    @Test
    @DisplayName("로그의_baseline_리비전을_못_찾으면_직전_리비전으로_대체한다")
    void 로그의_baseline_리비전을_못_찾으면_직전_리비전으로_대체한다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      Instant loggedForecastedAt = Instant.parse("2026-07-27T05:10:00Z");
      Weather previous = weatherOf(grid, D0, Instant.parse("2026-07-27T02:10:00Z"), 20.0);
      Weather latest = weatherOf(grid, D0, Instant.parse("2026-07-27T08:10:00Z"), 25.0);
      given(weatherRepository.findRecentTwoRevisions(grid, List.of(D0)))
          .willReturn(List.of(previous, latest));

      WeatherChangeNotificationLog log = WeatherChangeNotificationLog.create(grid, D0,
          loggedForecastedAt);
      given(notificationLogRepository.findByWeatherGridAndForecastAt(grid, D0))
          .willReturn(Optional.of(log));
      given(weatherRepository.findByWeatherGridAndForecastAtAndForecastedAt(grid, D0,
          loggedForecastedAt)).willReturn(Optional.empty());
      given(weatherChangeEvaluator.evaluate(any(), any())).willReturn(Optional.empty());

      // when
      notifier.detectAndNotify(baseTime);

      // then - 로그가 가리키는 리비전을 못 찾았으니 직전 리비전(previous)으로 폴백한다
      verify(weatherChangeEvaluator).evaluate(previous, latest);
    }

    @Test
    @DisplayName("baseTime이_2300이면_D0_D1_모두_대상에서_빠져_리비전_조회_자체를_하지_않는다")
    void baseTime이_2300이면_D0_D1_모두_대상에서_빠져_리비전_조회_자체를_하지_않는다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "2300");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(weatherRepository, never()).findRecentTwoRevisions(any(), anyList());
    }

    @Test
    @DisplayName("baseTime이_2000이면_D0와_D1_둘_다_대상에_포함된다")
    void baseTime이_2000이면_D0와_D1_둘_다_대상에_포함된다() {
      // given
      BaseTime baseTime = new BaseTime("20260727", "2000");
      WeatherGrid grid = gridOf(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(weatherRepository.findRecentTwoRevisions(eq(grid), anyList())).willReturn(List.of());

      // when
      notifier.detectAndNotify(baseTime);

      // then
      verify(weatherRepository).findRecentTwoRevisions(grid, List.of(D0, D1));
    }
  }
}