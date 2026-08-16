package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WeatherD1BaselineCustomRepository {

  List<WeatherD1BaselineRetentionItem> findForRetention(LocalDate cutoff,
      LocalDate lastTargetDate, UUID lastId, int limit);
}