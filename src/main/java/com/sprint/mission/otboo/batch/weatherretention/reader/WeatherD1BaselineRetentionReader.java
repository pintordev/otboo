package com.sprint.mission.otboo.batch.weatherretention.reader;

import com.sprint.mission.otboo.batch.weatherretention.config.WeatherRetentionProperties;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import java.time.Clock;
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
public class WeatherD1BaselineRetentionReader
    implements ItemReader<WeatherD1BaselineRetentionItem> {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherD1BaselineRepository weatherD1BaselineRepository;
  private final WeatherRetentionProperties properties;
  private final Clock clock;

  private LocalDate cutoff;
  private LocalDate lastTargetDate;
  private UUID lastId;
  private Iterator<WeatherD1BaselineRetentionItem> iterator;

  @Override
  public WeatherD1BaselineRetentionItem read() {
    if (cutoff == null) {
      // target_date가 오늘 이하인 baseline은 compareD1AndNotify()의 소비 주기(target_date - 1일)를
      // 이미 지났다는 뜻이라 그리드가 중간에 배치 대상에서 빠져 고아로 남은 것으로 간주한다(#163).
      cutoff = LocalDate.now(clock.withZone(KST));
      lastTargetDate = LocalDate.EPOCH;
      lastId = new UUID(0L, 0L);
      log.info("WeatherD1BaselineRetentionReader 시작: cutoff={}, chunkSize={}", cutoff,
          properties.chunkSize());
    }

    while (iterator == null || !iterator.hasNext()) {
      List<WeatherD1BaselineRetentionItem> items = weatherD1BaselineRepository.findForRetention(
          cutoff, lastTargetDate, lastId, properties.chunkSize());
      if (items.isEmpty()) {
        return null;
      }
      iterator = items.iterator();
      log.info("WeatherD1BaselineRetentionReader 페이지 로드 완료: size={}", items.size());
    }

    WeatherD1BaselineRetentionItem item = iterator.next();
    lastTargetDate = item.targetDate();
    lastId = item.id();
    return item;
  }
}