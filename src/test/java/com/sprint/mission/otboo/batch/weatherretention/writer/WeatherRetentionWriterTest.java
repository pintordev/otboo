package com.sprint.mission.otboo.batch.weatherretention.writer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
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

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

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
      // given - WeatherRetentionItem엔 @NotNull이 없어 FixtureMonkey가 가끔 id를 null로
      // 생성한다(List.of()가 null을 허용하지 않아 NPE로 이어짐) - 검증에 쓰는 id만 명시적으로 고정
      WeatherRetentionItem item1 = FIXTURE_MONKEY.giveMeBuilder(WeatherRetentionItem.class)
          .set("id", UUID.randomUUID())
          .sample();
      WeatherRetentionItem item2 = FIXTURE_MONKEY.giveMeBuilder(WeatherRetentionItem.class)
          .set("id", UUID.randomUUID())
          .sample();
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