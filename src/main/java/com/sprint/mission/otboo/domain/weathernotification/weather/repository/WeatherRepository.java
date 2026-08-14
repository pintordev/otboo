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

  Optional<Weather> findByWeatherGridAndForecastAtAndForecastedAt(WeatherGrid weatherGrid,
      Instant forecastAt, Instant forecastedAt);

  // findLatestRevisions()의 슬롯 단위 대체 - V14(유니크 제약 (weather_grid_id, forecast_at))
  // 이후로는 forecast_at당 row가 정확히 1개라 "최신 리비전 골라내기"(DISTINCT ON) 자체가 필요
  // 없다. WeatherService/WeatherRefresher가 갈아탄 뒤 findLatestRevisions는 삭제 완료.
  List<Weather> findAllByWeatherGridAndForecastAtGreaterThanEqual(WeatherGrid weatherGrid,
      Instant from);

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