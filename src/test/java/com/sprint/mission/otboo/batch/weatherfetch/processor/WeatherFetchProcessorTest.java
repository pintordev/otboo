package com.sprint.mission.otboo.batch.weatherfetch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.time.Clock;
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

@ExtendWith(MockitoExtension.class)
class WeatherFetchProcessorTest {

  @InjectMocks
  private WeatherFetchProcessor processor;

  @Mock
  private WeatherRefresher weatherRefresher;

  @Mock
  private Clock clock;

  @Nested
  @DisplayName("Process")
  class Process {

    @Test
    @DisplayName("WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다")
    void WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다() {
      // given
      given(clock.instant()).willReturn(Instant.parse("2026-07-27T08:30:00Z"));
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      List<Weather> saved = List.of();
      given(weatherRefresher.refresh(eq(weatherGrid), eq(new KmaGridPoint(60, 127)), any()))
          .willReturn(saved);

      // when
      List<Weather> result = processor.process(weatherGrid);

      // then
      assertThat(result).isEqualTo(saved);
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
      processor.process(grid1);
      processor.process(grid2);

      // then
      ArgumentCaptor<BaseTime> baseTimeCaptor = ArgumentCaptor.forClass(BaseTime.class);
      verify(weatherRefresher, times(2)).refresh(any(), any(), baseTimeCaptor.capture());
      List<BaseTime> capturedBaseTimes = baseTimeCaptor.getAllValues();
      assertThat(capturedBaseTimes.get(0)).isEqualTo(capturedBaseTimes.get(1));
    }
  }
}