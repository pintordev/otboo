package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.util.List;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class WeatherCacheProvider {

  @Cacheable(cacheNames = "weather",
      key = "#weatherGrid.x + ':' + #weatherGrid.y",
      unless = "#result.isEmpty()")
  public List<Weather> findCachedSlots(WeatherGrid weatherGrid) {
    return List.of();
  }

  @CachePut(cacheNames = "weather", key = "#weatherGrid.x + ':' + #weatherGrid.y")
  public List<Weather> putSlots(WeatherGrid weatherGrid, List<Weather> slots) {
    return slots;
  }
}