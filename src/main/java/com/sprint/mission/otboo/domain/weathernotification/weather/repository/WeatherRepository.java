package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO weathers (id, weather_grid_id, forecasted_at, forecast_at, sky_status,
          precipitation_type, precipitation_amount, precipitation_probability,
          humidity_current, humidity_compared, temperature_current, temperature_compared,
          temperature_min, temperature_max, wind_speed, wind_as_word, created_at)
      VALUES (:id, :weatherGridId, :forecastedAt, :forecastAt, :skyStatus, :precipitationType,
          :precipitationAmount, :precipitationProbability, :humidityCurrent, :humidityCompared,
          :temperatureCurrent, :temperatureCompared, :temperatureMin, :temperatureMax,
          :windSpeed, :windAsWord, now())
      ON CONFLICT (weather_grid_id, forecast_at, forecasted_at) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("id") UUID id, @Param("weatherGridId") UUID weatherGridId,
      @Param("forecastedAt") Instant forecastedAt, @Param("forecastAt") Instant forecastAt,
      @Param("skyStatus") String skyStatus, @Param("precipitationType") String precipitationType,
      @Param("precipitationAmount") double precipitationAmount,
      @Param("precipitationProbability") double precipitationProbability,
      @Param("humidityCurrent") double humidityCurrent,
      @Param("humidityCompared") double humidityCompared,
      @Param("temperatureCurrent") double temperatureCurrent,
      @Param("temperatureCompared") double temperatureCompared,
      @Param("temperatureMin") double temperatureMin,
      @Param("temperatureMax") double temperatureMax, @Param("windSpeed") double windSpeed,
      @Param("windAsWord") String windAsWord);
}