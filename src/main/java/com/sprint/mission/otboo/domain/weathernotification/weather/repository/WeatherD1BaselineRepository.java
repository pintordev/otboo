package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherD1BaselineRepository extends JpaRepository<WeatherD1Baseline, UUID> {

  Optional<WeatherD1Baseline> findByWeatherGridAndTargetDate(WeatherGrid weatherGrid,
      LocalDate targetDate);
}