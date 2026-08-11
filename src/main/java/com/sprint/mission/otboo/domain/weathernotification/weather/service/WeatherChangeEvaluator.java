package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class WeatherChangeEvaluator {

  private final WeatherChangeProperties properties;

  public Optional<ChangeResult> evaluate(Weather previous, Weather latest) {
    List<String> reasons = new ArrayList<>();

    double temperatureDelta = latest.getTemperatureCurrent() - previous.getTemperatureCurrent();
    if (Math.abs(temperatureDelta) >= properties.temperatureThreshold()) {
      reasons.add(temperatureMessage(temperatureDelta));
    }

    // 값 자체가 바뀌었는지만 본다 - NONE↔RAIN/SNOW/RAIN_SNOW뿐 아니라 RAIN↔SNOW 같은
    // 전환도 이걸로 잡힌다.
    if (previous.getPrecipitationType() != latest.getPrecipitationType()) {
      reasons.add(
          precipitationTypeMessage(previous.getPrecipitationType(), latest.getPrecipitationType()));
    }

    double probabilityDelta =
        latest.getPrecipitationProbability() - previous.getPrecipitationProbability();
    if (Math.abs(probabilityDelta) >= properties.precipitationProbabilityThreshold()) {
      reasons.add(precipitationProbabilityMessage(probabilityDelta));
    }

    double amountDelta = latest.getPrecipitationAmount() - previous.getPrecipitationAmount();
    if (Math.abs(amountDelta) >= properties.precipitationAmountThreshold()) {
      reasons.add(precipitationAmountMessage(amountDelta));
    }

    return reasons.isEmpty() ? Optional.empty()
        : Optional.of(new ChangeResult(latest.getWeatherGrid(), latest.getForecastAt(),
            latest.getForecastedAt(), reasons));
  }

  private String temperatureMessage(double delta) {
    return delta > 0
        ? "기온이 %.1f도 올랐어요.".formatted(Math.abs(delta))
        : "기온이 %.1f도 내렸어요.".formatted(Math.abs(delta));
  }

  private String precipitationTypeMessage(PrecipitationType previous, PrecipitationType latest) {
    return "강수 형태가 %s에서 %s(으)로 바뀌었어요.".formatted(previous, latest);
  }

  private String precipitationProbabilityMessage(double delta) {
    return "강수확률이 %.0f%%p %s어요.".formatted(Math.abs(delta), delta > 0 ? "올랐" : "내렸");
  }

  private String precipitationAmountMessage(double delta) {
    return "강수량이 %.1fmm %s어요.".formatted(Math.abs(delta), delta > 0 ? "늘었" : "줄었");
  }

  public record ChangeResult(WeatherGrid weatherGrid, Instant forecastAt,
      Instant latestForecastedAt, List<String> reasons) {

  }
}