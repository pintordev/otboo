package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;

public record PrecipitationDto(
    PrecipitationType type,
    double amount,
    double probability
) {

}