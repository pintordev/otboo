package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

public record TemperatureDto(
    double current,
    Double comparedToDayBefore,
    double min,
    double max
) {

}