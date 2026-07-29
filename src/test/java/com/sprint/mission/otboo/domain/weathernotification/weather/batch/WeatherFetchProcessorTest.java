package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherFetchProcessorTest {

  @InjectMocks
  private WeatherFetchProcessor processor;

  @Mock
  private Clock clock;

  @Nested
  @DisplayName("Process")
  class Process {

    @Test
    @DisplayName("WeatherGrid를_KmaGridPoint와_baseTime을_담은_WeatherFetchItem으로_변환한다")
    void WeatherGrid를_KmaGridPoint와_baseTime을_담은_WeatherFetchItem으로_변환한다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:30:00Z");
      given(clock.instant()).willReturn(now);
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);

      // when
      WeatherFetchItem result = processor.process(weatherGrid);

      // then
      assertThat(result.weatherGrid()).isEqualTo(weatherGrid);
      assertThat(result.grid()).isEqualTo(new KmaGridPoint(60, 127));
      assertThat(result.baseTime()).isEqualTo(KmaBaseTimeCalculator.calculate(now));
    }

    @Test
    @DisplayName("baseTime은_Step_동안_한_번만_계산해서_재사용한다")
    void baseTime은_Step_동안_한_번만_계산해서_재사용한다() {
      // given
      given(clock.instant()).willReturn(
          Instant.parse("2026-07-27T08:30:00Z"),
          Instant.parse("2026-07-27T20:30:00Z"));
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 128);

      // when
      WeatherFetchItem item1 = processor.process(grid1);
      WeatherFetchItem item2 = processor.process(grid2);

      // then
      assertThat(item1.baseTime()).isEqualTo(item2.baseTime());
    }
  }
}