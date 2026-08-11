package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherChangeNotificationLogRepository
    extends JpaRepository<WeatherChangeNotificationLog, UUID> {

  Optional<WeatherChangeNotificationLog> findByWeatherGridAndForecastAt(
      WeatherGrid weatherGrid, Instant forecastAt);
}