package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import tools.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LocationWriter {

  private final ObjectMapper mapper;
  private final LocationRepository locationRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Location save(int latBlock, int lonBlock, List<String> locationNames) {
    locationRepository.insertIfAbsent(UUID.randomUUID(), latBlock, lonBlock,
        mapper.writeValueAsString(locationNames));
    return locationRepository.findByLatBlockAndLonBlock(latBlock, lonBlock).orElseThrow();
  }
}