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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class WeatherWriter {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Weather> save(WeatherGrid weatherGrid, Instant forecastedAt,
      List<DailyWeatherForecastDto> dailyForecasts, Map<LocalDate, Weather> existingByDate) {
    Weather previousDayWeather = dailyForecasts.isEmpty() ? null
        : existingByDate.get(dailyForecasts.get(0).date().minusDays(1));
    Double previousTemp =
        previousDayWeather != null ? previousDayWeather.getTemperatureCurrent() : null;
    Double previousHumidity =
        previousDayWeather != null ? previousDayWeather.getHumidityCurrent() : null;

    List<Weather> saved = new ArrayList<>();
    for (DailyWeatherForecastDto dto : dailyForecasts) {
      double temperatureCompared =
          previousTemp != null ? dto.temperatureCurrent() - previousTemp : 0.0;
      double humidityCompared =
          previousHumidity != null ? dto.humidityCurrent() - previousHumidity : 0.0;

      Weather weather = Weather.create(weatherGrid, forecastedAt,
          dto.date().atStartOfDay(KST).toInstant(), dto.skyStatus(), dto.precipitationType(),
          dto.precipitationAmount(), dto.precipitationProbability(), dto.humidityCurrent(),
          humidityCompared, dto.temperatureCurrent(), temperatureCompared, dto.temperatureMin(),
          dto.temperatureMax(), dto.windSpeed(), toWindStrength(dto.windSpeed()));

      saved.add(weatherRepository.save(weather));

      previousTemp = dto.temperatureCurrent();
      previousHumidity = dto.humidityCurrent();
    }
    return saved;
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