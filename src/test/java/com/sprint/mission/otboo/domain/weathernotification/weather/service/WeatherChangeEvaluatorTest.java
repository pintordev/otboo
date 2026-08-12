package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WeatherChangeEvaluator")
class WeatherChangeEvaluatorTest {

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  private final WeatherChangeEvaluator evaluator = new WeatherChangeEvaluator(
      new WeatherChangeProperties(3.0, 30.0, 20.0));

  private Weather weatherOf(double temperatureCurrent, PrecipitationType precipitationType,
      double precipitationProbability, double precipitationAmount) {
    return ENTITY_FIXTURE_MONKEY.giveMeBuilder(Weather.class)
        .set("temperatureCurrent", temperatureCurrent)
        .set("precipitationType", precipitationType)
        .set("precipitationProbability", precipitationProbability)
        .set("precipitationAmount", precipitationAmount)
        .sample();
  }

  @Nested
  @DisplayName("Evaluate")
  class Evaluate {

    @Test
    @DisplayName("기온_델타가_임계값_미만이면_감지되지_않는다")
    void 기온_델타가_임계값_미만이면_감지되지_않는다() {
      Weather previous = weatherOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      Weather latest = weatherOf(22.9, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기온_델타가_임계값_이상이면_감지된다")
    void 기온_델타가_임계값_이상이면_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      Weather latest = weatherOf(23.0, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수_없음에서_비로_바뀌면_감지된다")
    void 강수_없음에서_비로_바뀌면_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      Weather latest = weatherOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("비에서_강수_없음으로_바뀌면_감지된다")
    void 비에서_강수_없음으로_바뀌면_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);
      Weather latest = weatherOf(20.0, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수_형태_전환_메시지는_enum_이름이_아니라_한글_표시명을_사용한다")
    void 강수_형태_전환_메시지는_enum_이름이_아니라_한글_표시명을_사용한다() {
      Weather previous = weatherOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      Weather latest = weatherOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).containsExactly("강수 형태가 없음에서 비 상태로 바뀌었어요.");
    }

    @Test
    @DisplayName("비에서_눈으로_전환도_감지된다")
    void 비에서_눈으로_전환도_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);
      Weather latest = weatherOf(20.0, PrecipitationType.SNOW, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수확률_델타가_임계값_이상이면_감지된다")
    void 강수확률_델타가_임계값_이상이면_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.NONE, 10.0, 0.0);
      Weather latest = weatherOf(20.0, PrecipitationType.NONE, 40.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수량_델타가_임계값_이상이면_타입과_확률이_그대로여도_감지된다")
    void 강수량_델타가_임계값_이상이면_타입과_확률이_그대로여도_감지된다() {
      Weather previous = weatherOf(20.0, PrecipitationType.RAIN, 80.0, 5.0);
      Weather latest = weatherOf(20.0, PrecipitationType.RAIN, 80.0, 25.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.get().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("어느_조건도_넘지_않으면_감지되지_않는다")
    void 어느_조건도_넘지_않으면_감지되지_않는다() {
      Weather previous = weatherOf(20.0, PrecipitationType.RAIN, 50.0, 5.0);
      Weather latest = weatherOf(21.0, PrecipitationType.RAIN, 55.0, 10.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isEmpty();
    }
  }
}