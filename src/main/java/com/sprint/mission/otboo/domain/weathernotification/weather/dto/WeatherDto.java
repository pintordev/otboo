package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.time.Instant;
import java.util.UUID;

public record WeatherDto(
    UUID id,
    Instant forecastedAt,
    Instant forecastAt,
    LocationDto location,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {

}