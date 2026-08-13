package com.sprint.mission.otboo.batch.weatherfetch.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WeatherFetchWriterTest {

  @InjectMocks
  private WeatherFetchWriter writer;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("Write")
  class Write {

    @Test
    @DisplayName("여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_한_번의_batchUpdate로_저장한다")
    void 여러_WeatherGrid의_List_Weather가_섞인_청크를_flatten해서_한_번의_batchUpdate로_저장한다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 128);
      Weather weather1 = weather(grid1);
      Weather weather2 = weather(grid2);
      Weather weather3 = weather(grid2);

      Chunk<List<Weather>> chunk = new Chunk<>(
          List.of(List.of(weather1), List.of(weather2, weather3)));
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1, 1, 1});

      // when
      writer.write(chunk);

      // then - 격자별 개별 insertIfAbsent 호출이 아니라, chunk 전체를 한 번의 batchUpdate로 저장한다
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(
          BatchPreparedStatementSetter.class);
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(jdbcTemplate, times(1)).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());
      assertThat(sqlCaptor.getValue()).contains("ON CONFLICT").contains("weathers");
      assertThat(setterCaptor.getValue().getBatchSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈_청크는_batchUpdate를_호출하지_않는다")
    void 빈_청크는_batchUpdate를_호출하지_않는다() {
      // given
      Chunk<List<Weather>> chunk = new Chunk<>(List.of());

      // when
      writer.write(chunk);

      // then
      verify(jdbcTemplate, never()).batchUpdate(anyString(),
          any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("일부_행이_충돌로_스킵돼도_예외_없이_끝난다")
    void 일부_행이_충돌로_스킵돼도_예외_없이_끝난다() {
      // given - ON CONFLICT DO NOTHING으로 스킵된 행은 batchUpdate 결과 배열에서 0으로 온다
      WeatherGrid grid = WeatherGrid.create(60, 127);
      Weather duplicated = weather(grid);
      Chunk<List<Weather>> chunk = new Chunk<>(List.of(List.of(duplicated, duplicated)));
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1, 0});

      // when & then
      assertThatCode(() -> writer.write(chunk)).doesNotThrowAnyException();
      verify(jdbcTemplate, times(1)).batchUpdate(anyString(),
          any(BatchPreparedStatementSetter.class));
    }
  }

  private Weather weather(WeatherGrid grid) {
    return Weather.create(grid, Instant.parse("2026-07-27T08:00:00Z"),
        Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0,
        60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK);
  }
}