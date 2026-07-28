package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class WeatherGridWriter {

  private final WeatherGridRepository weatherGridRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WeatherGrid save(int x, int y) {
    weatherGridRepository.insertIfAbsent(UUID.randomUUID(), x, y);
    return weatherGridRepository.findByXAndY(x, y).orElseThrow();
  }
}
