package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

public record HumidityDto(
    double current,
    double comparedToDayBefore
) {

}