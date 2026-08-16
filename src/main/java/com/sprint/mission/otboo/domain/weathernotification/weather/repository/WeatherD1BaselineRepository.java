package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherD1BaselineCustomRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherD1BaselineRepository
    extends JpaRepository<WeatherD1Baseline, UUID>, WeatherD1BaselineCustomRepository {

  Optional<WeatherD1Baseline> findByWeatherGridAndTargetDate(WeatherGrid weatherGrid,
      LocalDate targetDate);
}