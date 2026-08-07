package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WeatherCustomRepository {

  List<WeatherRetentionItem> findForRetention(Instant cutoff, Instant lastForecastAt,
      UUID lastId, int limit);
}