package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;

public record WeatherFetchItem(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {

}