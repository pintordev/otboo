package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
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
  private WeatherRefresher weatherRefresher;

  @Nested
  @DisplayName("Write")
  class Write {

    @Test
    @DisplayName("chunk의_각_WeatherFetchItem마다_WeatherRefresher_refresh를_호출한다")
    void chunk의_각_WeatherFetchItem마다_WeatherRefresher_refresh를_호출한다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 128);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherFetchItem item1 = new WeatherFetchItem(grid1, new KmaGridPoint(60, 127), baseTime);
      WeatherFetchItem item2 = new WeatherFetchItem(grid2, new KmaGridPoint(61, 128), baseTime);

      // when
      writer.write(new Chunk<>(List.of(item1, item2)));

      // then
      verify(weatherRefresher).refresh(grid1, new KmaGridPoint(60, 127), baseTime);
      verify(weatherRefresher).refresh(grid2, new KmaGridPoint(61, 128), baseTime);
    }
  }
}