package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;

public record WeatherChangeSnapshot(double temperatureCurrent,
    PrecipitationType precipitationType, double precipitationProbability,
    double precipitationAmount) {

  public static WeatherChangeSnapshot currentOf(Weather weather) {
    return new WeatherChangeSnapshot(weather.getTemperatureCurrent(),
        weather.getPrecipitationType(), weather.getPrecipitationProbability(),
        weather.getPrecipitationAmount());
  }

  public static WeatherChangeSnapshot baselineOf(Weather weather) {
    return new WeatherChangeSnapshot(weather.getBaselineTemperatureCurrent(),
        weather.getBaselinePrecipitationType(), weather.getBaselinePrecipitationProbability(),
        weather.getBaselinePrecipitationAmount());
  }
}