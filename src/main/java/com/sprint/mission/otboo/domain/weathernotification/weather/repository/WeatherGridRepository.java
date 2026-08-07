package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherGridCustomRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherGridRepository
    extends JpaRepository<WeatherGrid, UUID>, WeatherGridCustomRepository {

  Optional<WeatherGrid> findByXAndY(int x, int y);

  @Modifying
  @Query(value = """
      INSERT INTO weather_grids (id, x, y, created_at)
      VALUES (:id, :x, :y, now())
      ON CONFLICT (x, y) DO NOTHING
      """, nativeQuery = true)
  void insertIfAbsent(@Param("id") UUID id, @Param("x") int x, @Param("y") int y);
}