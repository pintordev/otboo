package com.sprint.mission.otboo.batch.weatherretention.writer;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
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
public class WeatherRetentionWriter implements ItemWriter<WeatherRetentionItem> {

  private final WeatherRepository weatherRepository;

  @Override
  public void write(Chunk<? extends WeatherRetentionItem> chunk) {
    if (chunk.isEmpty()) {
      return;
    }
    List<UUID> ids = chunk.getItems().stream().map(WeatherRetentionItem::id).toList();
    weatherRepository.deleteAllByIdInBatch(ids);
    log.info("WeatherRetentionWriter chunk 삭제 완료: size={}", ids.size());
  }
}