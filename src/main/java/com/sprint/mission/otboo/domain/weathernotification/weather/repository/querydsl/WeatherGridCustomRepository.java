package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WeatherGridCustomRepository {

  List<WeatherGrid> findPageByCursor(Instant lastCreatedAt, UUID lastId, int limit);

  List<WeatherGrid> findPageByCursorExcludingForecasted(Instant lastCreatedAt, UUID lastId,
      Instant forecastedAt, int limit);
}