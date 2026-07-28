package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, UUID> {

  Optional<Location> findByXAndY(int x, int y);

  @Modifying
  @Query(value = """
      INSERT INTO locations (id, x, y, latitude, longitude, created_at, updated_at)
      VALUES (:id, :x, :y, :latitude, :longitude, now(), now())
      ON CONFLICT (x, y) DO NOTHING
      """, nativeQuery = true)
  void insertIfAbsent(@Param("id") UUID id, @Param("x") int x, @Param("y") int y,
      @Param("latitude") double latitude, @Param("longitude") double longitude);
}