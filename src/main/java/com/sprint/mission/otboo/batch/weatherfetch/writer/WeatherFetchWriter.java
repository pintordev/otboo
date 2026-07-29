package com.sprint.mission.otboo.batch.weatherfetch.writer;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherFetchWriter implements ItemWriter<List<Weather>> {

  @Override
  public void write(Chunk<? extends List<Weather>> chunk) {
    int savedCount = chunk.getItems().stream()
        .mapToInt(List::size)
        .sum();
    log.info("WeatherFetchWriter chunk 처리 완료: gridCount={}, savedWeatherCount={}",
        chunk.size(), savedCount);
  }
}