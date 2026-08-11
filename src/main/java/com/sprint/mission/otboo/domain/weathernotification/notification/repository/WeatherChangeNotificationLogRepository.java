package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherChangeNotificationLogRepository
    extends JpaRepository<WeatherChangeNotificationLog, UUID> {

  Optional<WeatherChangeNotificationLog> findByWeatherGridAndForecastAt(
      WeatherGrid weatherGrid, Instant forecastAt);

  // D0/D1만 다루므로 오늘 이전 로그는 다시 조회될 일이 없다 - 매 회차 정리
  @Modifying
  @Query("DELETE FROM WeatherChangeNotificationLog l WHERE l.forecastAt < :before")
  int deleteByForecastAtBefore(@Param("before") Instant before);
}