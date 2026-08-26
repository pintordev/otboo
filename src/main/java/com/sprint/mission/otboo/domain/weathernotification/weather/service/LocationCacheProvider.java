package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LocationCacheProvider {

  private final LocationRepository locationRepository;

  @Cacheable(cacheNames = "location", key = "#latBlock + ':' + #lonBlock",
      unless = "#result == null || #result.isEmpty()")
  public Optional<List<String>> findCachedLocationNames(int latBlock, int lonBlock) {
    return locationRepository.findByLatBlockAndLonBlock(latBlock, lonBlock)
        .map(Location::getLocationNames);
  }
}