package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.LocationBlock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationBlockRepository extends JpaRepository<LocationBlock, UUID> {

  Optional<LocationBlock> findByLatBlockAndLonBlock(int latBlock, int lonBlock);

  @Modifying
  @Query(value = """
      INSERT INTO location_blocks (id, lat_block, lon_block, location_names, created_at)
      VALUES (:id, :latBlock, :lonBlock, CAST(:locationNamesJson AS jsonb), now())
      ON CONFLICT (lat_block, lon_block) DO NOTHING
      """, nativeQuery = true)
  void insertIfAbsent(@Param("id") UUID id, @Param("latBlock") int latBlock,
      @Param("lonBlock") int lonBlock, @Param("locationNamesJson") String locationNamesJson);
}