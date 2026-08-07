package com.sprint.mission.otboo.batch.weatherretention.writer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

@ExtendWith(MockitoExtension.class)
class WeatherRetentionWriterTest {

  @InjectMocks
  private WeatherRetentionWriter writer;

  @Mock
  private WeatherRepository weatherRepository;

  @Nested
  @DisplayName("Write")
  class Write {

    @Test
    @DisplayName("청크의_id만_추출해서_deleteAllByIdInBatch를_호출한다")
    void 청크의_id만_추출해서_deleteAllByIdInBatch를_호출한다() {
      // given
      WeatherRetentionItem item1 = new WeatherRetentionItem(UUID.randomUUID(), Instant.now());
      WeatherRetentionItem item2 = new WeatherRetentionItem(UUID.randomUUID(), Instant.now());
      Chunk<WeatherRetentionItem> chunk = new Chunk<>(List.of(item1, item2));

      // when
      writer.write(chunk);

      // then
      verify(weatherRepository).deleteAllByIdInBatch(List.of(item1.id(), item2.id()));
    }

    @Test
    @DisplayName("빈_청크는_repository를_아예_호출하지_않는다")
    void 빈_청크는_repository를_아예_호출하지_않는다() {
      // given
      Chunk<WeatherRetentionItem> chunk = new Chunk<>(List.of());

      // when
      writer.write(chunk);

      // then
      verifyNoInteractions(weatherRepository);
    }
  }
}