package com.sprint.mission.otboo.batch.weatherretention.writer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
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
class WeatherD1BaselineRetentionWriterTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @InjectMocks
  private WeatherD1BaselineRetentionWriter writer;

  @Mock
  private WeatherD1BaselineRepository weatherD1BaselineRepository;

  @Nested
  @DisplayName("Write")
  class Write {

    @Test
    @DisplayName("청크의_id만_추출해서_deleteAllByIdInBatch를_호출한다")
    void 청크의_id만_추출해서_deleteAllByIdInBatch를_호출한다() {
      // given
      WeatherD1BaselineRetentionItem item1 = FIXTURE_MONKEY
          .giveMeBuilder(WeatherD1BaselineRetentionItem.class)
          .set("id", UUID.randomUUID())
          .sample();
      WeatherD1BaselineRetentionItem item2 = FIXTURE_MONKEY
          .giveMeBuilder(WeatherD1BaselineRetentionItem.class)
          .set("id", UUID.randomUUID())
          .sample();
      Chunk<WeatherD1BaselineRetentionItem> chunk = new Chunk<>(List.of(item1, item2));

      // when
      writer.write(chunk);

      // then
      verify(weatherD1BaselineRepository).deleteAllByIdInBatch(List.of(item1.id(), item2.id()));
    }

    @Test
    @DisplayName("빈_청크는_repository를_아예_호출하지_않는다")
    void 빈_청크는_repository를_아예_호출하지_않는다() {
      // given
      Chunk<WeatherD1BaselineRetentionItem> chunk = new Chunk<>(List.of());

      // when
      writer.write(chunk);

      // then
      verifyNoInteractions(weatherD1BaselineRepository);
    }
  }
}