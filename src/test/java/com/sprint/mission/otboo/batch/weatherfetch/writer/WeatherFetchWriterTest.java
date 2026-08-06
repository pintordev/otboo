package com.sprint.mission.otboo.batch.weatherfetch.writer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_각각_insertIfAbsent로_저장한다")
    void 여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_각각_insertIfAbsent로_저장한다() {
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
      verify(weatherRepository, times(3)).insertIfAbsent(any(), any(), any(), any(), anyString(),
          anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
          anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
      verify(weatherRepository).insertIfAbsent(eq(weather1.getId()), eq(grid1.getId()), any(),
          any(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
          anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("빈_청크는_insertIfAbsent를_호출하지_않는다")
    void 빈_청크는_insertIfAbsent를_호출하지_않는다() {
      // given
      Chunk<List<Weather>> chunk = new Chunk<>(List.of());

      // when
      writer.write(chunk);

      // then
      verify(weatherRepository, never()).insertIfAbsent(any(), any(), any(), any(), anyString(),
          anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
          anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
    }
  }

  private Weather weather(WeatherGrid grid) {
    return Weather.create(grid, Instant.parse("2026-07-27T08:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0,
        60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK);
  }
}