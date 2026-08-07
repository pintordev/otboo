package com.sprint.mission.otboo.batch.weatherretention.reader;

import com.sprint.mission.otboo.batch.weatherretention.config.WeatherRetentionProperties;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@RequiredArgsConstructor
@Component
public class WeatherRetentionReader implements ItemReader<WeatherRetentionItem> {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final WeatherRetentionProperties properties;
  private final Clock clock;

  private Instant cutoff;
  private Instant lastForecastAt;
  private UUID lastId;
  private Iterator<WeatherRetentionItem> iterator;

  @Override
  public WeatherRetentionItem read() {
    if (cutoff == null) {
      LocalDate today = LocalDate.now(clock.withZone(KST));
      cutoff = today.minusDays(properties.retentionDays()).atStartOfDay(KST).toInstant();
      lastForecastAt = Instant.EPOCH;
      lastId = new UUID(0L, 0L);
      log.info("WeatherRetentionReader 시작: cutoff={}, chunkSize={}", cutoff,
          properties.chunkSize());
    }

    while (iterator == null || !iterator.hasNext()) {
      List<WeatherRetentionItem> items = weatherRepository.findForRetention(cutoff,
          lastForecastAt, lastId, properties.chunkSize());
      if (items.isEmpty()) {
        return null;
      }
      iterator = items.iterator();
      log.info("WeatherRetentionReader 페이지 로드 완료: size={}", items.size());
    }

    WeatherRetentionItem item = iterator.next();
    lastForecastAt = item.forecastAt();
    lastId = item.id();
    return item;
  }
}