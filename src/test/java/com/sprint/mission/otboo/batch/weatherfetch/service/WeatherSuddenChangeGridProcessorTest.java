package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator.ChangeResult;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherSuddenChangeGridProcessor")
class WeatherSuddenChangeGridProcessorTest {

  private static final Instant FORECASTED_AT = Instant.parse("2026-07-27T08:00:00Z");

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private ProfileRepository profileRepository;
  @Mock
  private WeatherChangeEvaluator weatherChangeEvaluator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private WeatherSuddenChangeGridProcessor gridProcessor;

  @BeforeEach
  void setUp() {
    gridProcessor = new WeatherSuddenChangeGridProcessor(weatherRepository, profileRepository,
        weatherChangeEvaluator, eventPublisher);
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
    return Weather.create(grid, FORECASTED_AT, forecastAt, SkyStatus.CLEAR,
        PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, currentTemperature, 0.0, 25.0, 31.0, 2.5,
        WindStrength.WEAK, baselineTemperature, PrecipitationType.NONE, 0.0, 0.0);
  }

  @Nested
  @DisplayName("EvaluateD0")
  class EvaluateD0 {

    @Test
    @DisplayName("baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다")
    void baseline과_current가_임계값_이상_다르면_발행하고_baseline을_리셋한다() {
      // given
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, FORECASTED_AT, 20.0, 25.0);
      given(weatherChangeEvaluator.evaluate(WeatherChangeSnapshot.baselineOf(target),
          WeatherChangeSnapshot.currentOf(target)))
          .willReturn(Optional.of(new ChangeResult(List.of("기온이 5.0도 올랐어요."))));
      Profile profile = profileWithLocation(List.of("서울특별시", "강남구"));
      given(profileRepository.findByLocation(grid.getX(), grid.getY()))
          .willReturn(List.of(profile));

      // when
      boolean notified = gridProcessor.evaluateD0(target);

      // then
      assertThat(notified).isTrue();
      verify(eventPublisher).publishEvent(any(NotificationRequestedEvent.class));
      verify(weatherRepository).updateBaseline(target.getId(), 25.0, PrecipitationType.NONE, 0.0,
          0.0);
    }

    @Test
    @DisplayName("임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다")
    void 임계값_미만이면_발행하지_않고_baseline도_리셋하지_않는다() {
      // given - weatherChangeEvaluator는 mock이라 별도 stub 없으면 Optional.empty()를
      // 기본 반환한다
      WeatherGrid grid = gridWithId(60, 127);
      Weather target = weatherWithBaseline(grid, FORECASTED_AT, 20.0, 20.5);

      // when
      boolean notified = gridProcessor.evaluateD0(target);

      // then
      assertThat(notified).isFalse();
      verify(eventPublisher, never()).publishEvent(any());
      verify(weatherRepository, never()).updateBaseline(any(), anyDouble(), any(), anyDouble(),
          anyDouble());
    }
  }
}