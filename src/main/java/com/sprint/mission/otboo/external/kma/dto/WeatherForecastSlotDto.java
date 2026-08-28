package com.sprint.mission.otboo.external.kma.dto;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.time.Instant;
import java.time.LocalDate;

public record WeatherForecastSlotDto(
    LocalDate date,
    Instant slotAt,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    double precipitationAmount,
    double precipitationProbability,
    double humidityCurrent,
    double temperatureCurrent,
    double temperatureMin,
    double temperatureMax,
    double windSpeed,
    SkyStatus skyStatusWorst,
    PrecipitationType precipitationTypeMode,
    double humidityMax
) {

}