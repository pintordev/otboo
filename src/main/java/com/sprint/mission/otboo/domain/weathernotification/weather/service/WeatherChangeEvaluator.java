package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class WeatherChangeEvaluator {

  // 기상청 관측값은 소수 1자리라 비교 정밀도를 데이터에 맞춘다 - double 뺄셈 그대로 비교하면
  // "정확히 3.0도 차이"인 1.1→4.1 같은 값이 2.9999999999999996으로 미감지된다(PR #131 리뷰).
  private static final double SCALE = 10.0;

  private final WeatherChangeProperties properties;

  public Optional<ChangeResult> evaluate(WeatherChangeSnapshot previous,
      WeatherChangeSnapshot latest) {
    List<String> reasons = new ArrayList<>();

    double temperatureDelta = normalize(latest.temperatureCurrent()
        - previous.temperatureCurrent());
    if (Math.abs(temperatureDelta) >= properties.temperatureThreshold()) {
      reasons.add(temperatureMessage(temperatureDelta));
    }

    // 값 자체가 바뀌었는지만 본다 - NONE↔RAIN/SNOW/RAIN_SNOW뿐 아니라 RAIN↔SNOW 같은
    // 전환도 이걸로 잡힌다.
    if (previous.precipitationType() != latest.precipitationType()) {
      reasons.add(
          precipitationTypeMessage(previous.precipitationType(), latest.precipitationType()));
    }

    double probabilityDelta = normalize(latest.precipitationProbability()
        - previous.precipitationProbability());
    if (Math.abs(probabilityDelta) >= properties.precipitationProbabilityThreshold()) {
      reasons.add(precipitationProbabilityMessage(probabilityDelta));
    }

    double amountDelta = normalize(latest.precipitationAmount()
        - previous.precipitationAmount());
    if (Math.abs(amountDelta) >= properties.precipitationAmountThreshold()) {
      reasons.add(precipitationAmountMessage(amountDelta));
    }

    return reasons.isEmpty() ? Optional.empty() : Optional.of(new ChangeResult(reasons));
  }

  private double normalize(double delta) {
    return Math.round(delta * SCALE) / SCALE;
  }

  private String temperatureMessage(double delta) {
    return delta > 0
        ? "기온이 %.1f도 올랐어요.".formatted(Math.abs(delta))
        : "기온이 %.1f도 내렸어요.".formatted(Math.abs(delta));
  }

  private String precipitationTypeMessage(PrecipitationType previous, PrecipitationType latest) {
    // "%s(으)로"처럼 조사 선택 표기를 그대로 노출하면 안 된다 - "상태로"에 고정해 라벨 값(받침
    // 유무)과 무관하게 항상 자연스러운 문장이 되도록 한다(CodeRabbit PR #131 리뷰)
    return "강수 형태가 %s에서 %s 상태로 바뀌었어요.".formatted(previous.getLabel(), latest.getLabel());
  }

  private String precipitationProbabilityMessage(double delta) {
    return "강수확률이 %.0f%%p %s어요.".formatted(Math.abs(delta), delta > 0 ? "올랐" : "내렸");
  }

  private String precipitationAmountMessage(double delta) {
    return "강수량이 %.1fmm %s어요.".formatted(Math.abs(delta), delta > 0 ? "늘었" : "줄었");
  }

  public record ChangeResult(List<String> reasons) {

    // "reasons → 최종 문구" 조립 책임을 이 레코드로 모은다 - 이전엔 배치 패키지의
    // WeatherSuddenChangeChunkProcessor가 지역명 접두어를 붙여 content를 완성했는데, 문구를
    // 바꾸려면 두 파일을 열어야 했다(PR #131 리뷰). regionPrefix는 호출부가 위치 도메인
    // 지식(locationNames 그룹핑)으로 만들어 넘긴다 - 이 레코드는 위치를 모른다.
    public String content(String regionPrefix) {
      return regionPrefix + String.join(" ", reasons);
    }
  }
}