package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {

  @Query(value = """
      SELECT DISTINCT ON (forecast_at) *
      FROM weathers
      WHERE weather_grid_id = :#{#weatherGrid.id} AND forecast_at >= :from
      ORDER BY forecast_at, forecasted_at DESC
      """, nativeQuery = true)
  List<Weather> findLatestRevisions(@Param("weatherGrid") WeatherGrid weatherGrid,
      @Param("from") Instant from);
}