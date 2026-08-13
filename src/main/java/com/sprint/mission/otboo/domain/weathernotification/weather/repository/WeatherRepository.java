package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRepository extends JpaRepository<Weather, UUID>, WeatherCustomRepository {

  @Query(value = """
      SELECT DISTINCT ON (forecast_at) *
      FROM weathers
      WHERE weather_grid_id = :#{#weatherGrid.id} AND forecast_at >= :from
      ORDER BY forecast_at, forecasted_at DESC
      """, nativeQuery = true)
  List<Weather> findLatestRevisions(@Param("weatherGrid") WeatherGrid weatherGrid,
      @Param("from") Instant from);

  Optional<Weather> findByWeatherGridAndForecastAtAndForecastedAt(WeatherGrid weatherGrid,
      Instant forecastAt, Instant forecastedAt);

  List<Weather> findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(
      WeatherGrid weatherGrid, Instant forecastedAt, List<Instant> forecastAts);

  @Query(value = """
      SELECT * FROM (
        SELECT *, ROW_NUMBER() OVER (PARTITION BY forecast_at ORDER BY forecasted_at DESC) AS rn
        FROM weathers
        WHERE weather_grid_id = :#{#weatherGrid.id} AND forecast_at IN (:forecastAts)
      ) ranked
      WHERE rn <= 2
      ORDER BY forecast_at, forecasted_at DESC
      """, nativeQuery = true)
  List<Weather> findRecentTwoRevisions(@Param("weatherGrid") WeatherGrid weatherGrid,
      @Param("forecastAts") List<Instant> forecastAts);

  @Query("SELECT DISTINCT w.weatherGrid FROM Weather w WHERE w.forecastedAt = :forecastedAt")
  List<WeatherGrid> findGridsUpdatedAt(@Param("forecastedAt") Instant forecastedAt);
}