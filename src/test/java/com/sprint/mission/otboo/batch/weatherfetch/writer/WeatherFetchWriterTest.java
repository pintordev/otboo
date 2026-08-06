package com.sprint.mission.otboo.batch.weatherfetch.writer;

import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

@ExtendWith(MockitoExtension.class)
class WeatherFetchWriterTest {

  @InjectMocks
  private WeatherFetchWriter writer;

  @Mock
  private WeatherRepository weatherRepository;

  @Nested
  @DisplayName("Write")
  class Write {

    @Test
    @DisplayName("여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_saveAll에_넘긴다")
    void 여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_saveAll에_넘긴다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 128);
      Weather weather1 = weather(grid1);
      Weather weather2 = weather(grid2);
      Weather weather3 = weather(grid2);

      Chunk<List<Weather>> chunk = new Chunk<>(
          List.of(List.of(weather1), List.of(weather2, weather3)));

      // when
      writer.write(chunk);

      // then
      verify(weatherRepository).saveAll(List.of(weather1, weather2, weather3));
    }

    @Test
    @DisplayName("빈_청크는_saveAll을_빈_리스트로_호출한다")
    void 빈_청크는_saveAll을_빈_리스트로_호출한다() {
      // given
      Chunk<List<Weather>> chunk = new Chunk<>(List.of());

      // when
      writer.write(chunk);

      // then
      verify(weatherRepository).saveAll(List.of());
    }
  }

  private Weather weather(WeatherGrid grid) {
    return Weather.create(grid, Instant.parse("2026-07-27T08:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0,
        60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK);
  }
}