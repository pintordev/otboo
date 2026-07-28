package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.LocationBlock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationBlockRepository extends JpaRepository<LocationBlock, UUID> {

  Optional<LocationBlock> findByLatBlockAndLonBlock(int latBlock, int lonBlock);
}