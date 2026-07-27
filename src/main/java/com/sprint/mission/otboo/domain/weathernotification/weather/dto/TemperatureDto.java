package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

public record TemperatureDto(
    double current,
    double comparedToDayBefore,
    double min,
    double max
) {

}