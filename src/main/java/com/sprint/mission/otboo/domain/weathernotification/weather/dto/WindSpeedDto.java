package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;

public record WindSpeedDto(
    double speed,
    WindStrength asWord
) {

}