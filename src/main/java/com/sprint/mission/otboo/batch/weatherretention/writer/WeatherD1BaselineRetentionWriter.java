package com.sprint.mission.otboo.batch.weatherretention.writer;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
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
public class WeatherD1BaselineRetentionWriter
    implements ItemWriter<WeatherD1BaselineRetentionItem> {

  private final WeatherD1BaselineRepository weatherD1BaselineRepository;

  @Override
  public void write(Chunk<? extends WeatherD1BaselineRetentionItem> chunk) {
    if (chunk.isEmpty()) {
      return;
    }
    List<UUID> ids = chunk.getItems().stream().map(WeatherD1BaselineRetentionItem::id).toList();
    weatherD1BaselineRepository.deleteAllByIdInBatch(ids);
    log.info("WeatherD1BaselineRetentionWriter chunk 삭제 완료: size={}", ids.size());
  }
}