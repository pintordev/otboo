package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocationWriter {

  private final ObjectMapper mapper = new ObjectMapper();
  private final LocationRepository locationRepository;

  public LocationWriter(LocationRepository locationRepository) {
    this.locationRepository = locationRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Location save(int latBlock, int lonBlock, List<String> locationNames) {
    locationRepository.insertIfAbsent(UUID.randomUUID(), latBlock, lonBlock,
        toJson(locationNames));
    return locationRepository.findByLatBlockAndLonBlock(latBlock, lonBlock).orElseThrow();
  }

  private String toJson(List<String> locationNames) {
    try {
      return mapper.writeValueAsString(locationNames);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("locationNames 직렬화 실패", e);
    }
  }
}