package com.sprint.mission.otboo.batch.weatherfetch.reader;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class WeatherFetchReader implements ItemReader<WeatherGrid> {

  private final WeatherGridRepository weatherGridRepository;

  @Value("${batch.weather-fetch.chunk-size}")
  private int chunkSize;

  private Instant lastCreatedAt;
  private UUID lastId;
  private Iterator<WeatherGrid> iterator;

  @Override
  public WeatherGrid read() {
    if (lastCreatedAt == null) {
      lastCreatedAt = Instant.EPOCH;
      lastId = new UUID(0L, 0L);
      log.info("WeatherFetchReader 시작: chunkSize={}", chunkSize);
    }

    while (iterator == null || !iterator.hasNext()) {
      List<WeatherGrid> items = weatherGridRepository.findPageByCursor(lastCreatedAt, lastId,
          PageRequest.of(0, chunkSize));

      if (items.isEmpty()) {
        return null;
      }

      iterator = items.iterator();
      log.info("WeatherFetchReader 페이지 로드 완료: size={}", items.size());
    }

    WeatherGrid item = iterator.next();
    lastCreatedAt = item.getCreatedAt();
    lastId = item.getId();
    return item;
  }
}