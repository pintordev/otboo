package com.sprint.mission.otboo.domain.weathernotification.weather.dto;

import java.util.List;

public record LocationDto(
    double latitude,
    double longitude,
    int x,
    int y,
    List<String> locationNames
) {

}