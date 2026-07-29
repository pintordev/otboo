package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherFetchWriter implements ItemWriter<WeatherFetchItem> {

  private final WeatherRefresher weatherRefresher;

  @Override
  public void write(Chunk<? extends WeatherFetchItem> chunk) {
    for (WeatherFetchItem item : chunk) {
      weatherRefresher.refresh(item.weatherGrid(), item.grid(), item.baseTime());
    }
    log.info("WeatherFetchWriter chunk 처리 완료: gridCount={}", chunk.size());
  }
}