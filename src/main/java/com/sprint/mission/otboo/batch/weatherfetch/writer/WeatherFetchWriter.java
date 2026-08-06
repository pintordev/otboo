package com.sprint.mission.otboo.batch.weatherfetch.writer;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherFetchWriter implements ItemWriter<List<Weather>> {

  private final WeatherRepository weatherRepository;

  @Override
  public void write(Chunk<? extends List<Weather>> chunk) {
    List<Weather> all = chunk.getItems().stream().flatMap(List::stream).toList();
    weatherRepository.saveAll(all);
    log.info("WeatherFetchWriter chunk 저장 완료: gridCount={}, savedWeatherCount={}",
        chunk.size(), all.size());
  }
}