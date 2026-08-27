package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("날씨 변화 평가")
class WeatherChangeEvaluatorTest {

  private final WeatherChangeEvaluator evaluator = new WeatherChangeEvaluator(
      new WeatherChangeProperties(3.0, 30.0, 20.0, 500));

  private WeatherChangeSnapshot snapshotOf(double temperatureCurrent,
      PrecipitationType precipitationType, double precipitationProbability,
      double precipitationAmount) {
    return new WeatherChangeSnapshot(temperatureCurrent, precipitationType,
        precipitationProbability, precipitationAmount);
  }

  @Nested
  @DisplayName("평가")
  class Evaluate {

    @Test
    @DisplayName("기온_델타가_임계값_미만이면_감지되지_않는다")
    void 기온_델타가_임계값_미만이면_감지되지_않는다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(22.9, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기온_델타가_임계값_이상이면_감지된다")
    void 기온_델타가_임계값_이상이면_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(23.0, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수_없음에서_비로_바뀌면_감지된다")
    void 강수_없음에서_비로_바뀌면_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("비에서_강수_없음으로_바뀌면_감지된다")
    void 비에서_강수_없음으로_바뀌면_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수_형태_전환_메시지는_enum_이름이_아니라_한글_표시명을_사용한다")
    void 강수_형태_전환_메시지는_enum_이름이_아니라_한글_표시명을_사용한다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).containsExactly("강수 형태가 없음에서 비 상태로 바뀌었어요.");
    }

    @Test
    @DisplayName("비에서_눈으로_전환도_감지된다")
    void 비에서_눈으로_전환도_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.RAIN, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.SNOW, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수확률_델타가_임계값_이상이면_감지된다")
    void 강수확률_델타가_임계값_이상이면_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.NONE, 10.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.NONE, 40.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("강수량_델타가_임계값_이상이면_타입과_확률이_그대로여도_감지된다")
    void 강수량_델타가_임계값_이상이면_타입과_확률이_그대로여도_감지된다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.RAIN, 80.0, 5.0);
      WeatherChangeSnapshot latest = snapshotOf(20.0, PrecipitationType.RAIN, 80.0, 25.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("부동소수점_오차로_정확히_임계값만큼_변해도_미감지되지_않는다")
    void 부동소수점_오차로_정확히_임계값만큼_변해도_미감지되지_않는다() {
      // 1.1 -> 4.1은 사람 눈엔 정확히 3.0도 차이지만, double 뺄셈은 2.9999999999999996을
      // 낸다(PR #131 리뷰) - 정밀도 정규화 없이 그대로 >=로 비교하면 미감지된다
      WeatherChangeSnapshot previous = snapshotOf(1.1, PrecipitationType.NONE, 0.0, 0.0);
      WeatherChangeSnapshot latest = snapshotOf(4.1, PrecipitationType.NONE, 0.0, 0.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).hasSize(1);
    }

    @Test
    @DisplayName("어느_조건도_넘지_않으면_감지되지_않는다")
    void 어느_조건도_넘지_않으면_감지되지_않는다() {
      WeatherChangeSnapshot previous = snapshotOf(20.0, PrecipitationType.RAIN, 50.0, 5.0);
      WeatherChangeSnapshot latest = snapshotOf(21.0, PrecipitationType.RAIN, 55.0, 10.0);

      Optional<WeatherChangeEvaluator.ChangeResult> result = evaluator.evaluate(previous, latest);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("하루 요약 평가")
  class EvaluateDaySummary {

    @Test
    @DisplayName("여러_시각이_모두_임계값을_넘어도_기온_문구는_델타가_가장_큰_시각_1개만_남는다")
    void 여러_시각이_모두_임계값을_넘어도_기온_문구는_델타가_가장_큰_시각_1개만_남는다() {
      // given - 하루 종일 기온이 비슷한 폭으로 오르는 날, 마지막 시각이 가장 큰 폭
      List<WeatherChangeSnapshot> baselines = List.of(
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0),
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0),
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0));
      List<WeatherChangeSnapshot> currents = List.of(
          snapshotOf(23.0, PrecipitationType.NONE, 0.0, 0.0), // +3.0
          snapshotOf(23.5, PrecipitationType.NONE, 0.0, 0.0), // +3.5
          snapshotOf(24.0, PrecipitationType.NONE, 0.0, 0.0)); // +4.0(최댓값)

      // when
      Optional<WeatherChangeEvaluator.ChangeResult> result =
          evaluator.evaluateDaySummary(baselines, currents);

      // then - 24개까지 반복되던 문구가 최댓값(+4.0) 1개로만 남는다
      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).containsExactly("기온이 4.0도 올랐어요.");
    }

    @Test
    @DisplayName("강수_형태가_하루_중_한_번이라도_바뀌면_문구가_하나_포함된다")
    void 강수_형태가_하루_중_한_번이라도_바뀌면_문구가_하나_포함된다() {
      // given
      List<WeatherChangeSnapshot> baselines = List.of(
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0),
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0));
      List<WeatherChangeSnapshot> currents = List.of(
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0),
          snapshotOf(20.0, PrecipitationType.RAIN, 0.0, 0.0));

      // when
      Optional<WeatherChangeEvaluator.ChangeResult> result =
          evaluator.evaluateDaySummary(baselines, currents);

      // then
      assertThat(result).isPresent();
      assertThat(result.orElseThrow().reasons()).containsExactly("강수 형태가 없음에서 비 상태로 바뀌었어요.");
    }

    @Test
    @DisplayName("어느_지표도_임계값을_넘지_않으면_감지되지_않는다")
    void 어느_지표도_임계값을_넘지_않으면_감지되지_않는다() {
      // given
      List<WeatherChangeSnapshot> baselines = List.of(
          snapshotOf(20.0, PrecipitationType.RAIN, 50.0, 5.0),
          snapshotOf(20.0, PrecipitationType.RAIN, 50.0, 5.0));
      List<WeatherChangeSnapshot> currents = List.of(
          snapshotOf(21.0, PrecipitationType.RAIN, 55.0, 10.0),
          snapshotOf(21.0, PrecipitationType.RAIN, 55.0, 10.0));

      // when
      Optional<WeatherChangeEvaluator.ChangeResult> result =
          evaluator.evaluateDaySummary(baselines, currents);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("baselines와_currents의_크기가_다르면_예외가_발생한다")
    void baselines와_currents의_크기가_다르면_예외가_발생한다() {
      // given
      List<WeatherChangeSnapshot> baselines = List.of(
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0),
          snapshotOf(20.0, PrecipitationType.NONE, 0.0, 0.0));
      List<WeatherChangeSnapshot> currents = List.of(
          snapshotOf(21.0, PrecipitationType.NONE, 0.0, 0.0));

      // when / then
      assertThatThrownBy(() -> evaluator.evaluateDaySummary(baselines, currents))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("변화 결과")
  class ChangeResultTest {

    @Test
    @DisplayName("Content는_지역명_접두어와_reasons를_합쳐_문구를_만든다")
    void Content는_지역명_접두어와_reasons를_합쳐_문구를_만든다() {
      WeatherChangeEvaluator.ChangeResult result = new WeatherChangeEvaluator.ChangeResult(
          List.of("기온이 3.0도 올랐어요.", "강수량이 5.0mm 늘었어요."));

      String content = result.content("강남구 ");

      assertThat(content).isEqualTo("강남구 기온이 3.0도 올랐어요. 강수량이 5.0mm 늘었어요.");
    }

    @Test
    @DisplayName("Content는_지역명_접두어가_없으면_reasons만_이어붙인다")
    void Content는_지역명_접두어가_없으면_reasons만_이어붙인다() {
      WeatherChangeEvaluator.ChangeResult result = new WeatherChangeEvaluator.ChangeResult(
          List.of("기온이 3.0도 올랐어요."));

      String content = result.content("");

      assertThat(content).isEqualTo("기온이 3.0도 올랐어요.");
    }
  }
}