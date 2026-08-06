package com.sprint.mission.otboo.batch.weatherfetch.writer;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.util.List;
import java.util.UUID;
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
    int insertedCount = 0;
    for (Weather weather : all) {
      insertedCount += weatherRepository.insertIfAbsent(UUID.randomUUID(),
          weather.getWeatherGrid().getId(), weather.getForecastedAt(), weather.getForecastAt(),
          weather.getSkyStatus().name(), weather.getPrecipitationType().name(),
          weather.getPrecipitationAmount(), weather.getPrecipitationProbability(),
          weather.getHumidityCurrent(), weather.getHumidityCompared(),
          weather.getTemperatureCurrent(), weather.getTemperatureCompared(),
          weather.getTemperatureMin(), weather.getTemperatureMax(), weather.getWindSpeed(),
          weather.getWindAsWord().name());
    }
    log.info("WeatherFetchWriter chunk 저장 완료: gridCount={}, 시도={}, 실insert={}", chunk.size(),
        all.size(), insertedCount);
  }
}