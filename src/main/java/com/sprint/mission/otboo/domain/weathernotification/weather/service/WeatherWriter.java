package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 클래스 레벨 @Transactional(readOnly = true)를 의도적으로 두지 않는다 - WeatherRefresher와
// 동일한 이유로, 배치(WeatherFetchWriter) 청크 트랜잭션 안에서도 build()가 호출된다. save()는
// 이미 REQUIRES_NEW로 API 전용 쓰기 경계를 명시하고, build()는 순수 변환이라 트랜잭션이 필요 없다.
@RequiredArgsConstructor
@Component
public class WeatherWriter {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;

  public List<Weather> build(WeatherGrid weatherGrid, Instant forecastedAt,
      List<DailyWeatherForecastDto> dailyForecasts, Map<LocalDate, Weather> existingByDate) {
    Weather previousDayWeather = dailyForecasts.isEmpty() ? null
        : existingByDate.get(dailyForecasts.get(0).date().minusDays(1));
    Double previousTemp =
        previousDayWeather != null ? previousDayWeather.getTemperatureCurrent() : null;
    Double previousHumidity =
        previousDayWeather != null ? previousDayWeather.getHumidityCurrent() : null;

    List<Weather> built = new ArrayList<>();
    for (DailyWeatherForecastDto dto : dailyForecasts) {
      double temperatureCompared =
          previousTemp != null ? dto.temperatureCurrent() - previousTemp : 0.0;
      double humidityCompared =
          previousHumidity != null ? dto.humidityCurrent() - previousHumidity : 0.0;

      built.add(Weather.create(weatherGrid, forecastedAt,
          dto.date().atStartOfDay(KST).toInstant(), dto.skyStatus(), dto.precipitationType(),
          dto.precipitationAmount(), dto.precipitationProbability(), dto.humidityCurrent(),
          humidityCompared, dto.temperatureCurrent(), temperatureCompared, dto.temperatureMin(),
          dto.temperatureMax(), dto.windSpeed(), toWindStrength(dto.windSpeed())));

      previousTemp = dto.temperatureCurrent();
      previousHumidity = dto.humidityCurrent();
    }
    return built;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Weather> save(WeatherGrid weatherGrid, Instant forecastedAt,
      List<DailyWeatherForecastDto> dailyForecasts, Map<LocalDate, Weather> existingByDate) {
    List<Weather> built = build(weatherGrid, forecastedAt, dailyForecasts, existingByDate);
    return built.stream()
        .map(weather -> {
          weatherRepository.insertIfAbsent(UUID.randomUUID(), weatherGrid.getId(), forecastedAt,
              weather.getForecastAt(), weather.getSkyStatus().name(),
              weather.getPrecipitationType().name(), weather.getPrecipitationAmount(),
              weather.getPrecipitationProbability(), weather.getHumidityCurrent(),
              weather.getHumidityCompared(), weather.getTemperatureCurrent(),
              weather.getTemperatureCompared(), weather.getTemperatureMin(),
              weather.getTemperatureMax(), weather.getWindSpeed(),
              weather.getWindAsWord().name());
          return weatherRepository
              .findByWeatherGridAndForecastAtAndForecastedAt(weatherGrid, weather.getForecastAt(),
                  forecastedAt)
              .orElseThrow();
        })
        .toList();
  }

  private WindStrength toWindStrength(double speed) {
    if (speed < 4.0) {
      return WindStrength.WEAK;
    }
    if (speed < 9.0) {
      return WindStrength.MODERATE;
    }
    return WindStrength.STRONG;
  }
}