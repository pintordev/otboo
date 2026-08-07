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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherFetchProcessorTest {

  private static final BaseTime BASE_TIME = new BaseTime("20260727", "1700");

  @Mock
  private WeatherRefresher weatherRefresher;

  private WeatherFetchProcessor processor;

  @BeforeEach
  void setUp() {
    // JobExecutionContext에서 SpEL로 주입받는 값을 생성자 인자로 직접 대신한다
    processor = new WeatherFetchProcessor(weatherRefresher, BASE_TIME.baseDate(),
        BASE_TIME.baseTime());
  }

  @Nested
  @DisplayName("Process")
  class Process {

    @Test
    @DisplayName("WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다")
    void WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      List<Weather> saved = List.of();
      given(weatherRefresher.build(eq(weatherGrid), eq(new KmaGridPoint(60, 127)), eq(BASE_TIME)))
          .willReturn(saved);

      // when
      List<Weather> result = processor.process(weatherGrid);

      // then
      assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("여러_격자를_처리해도_JobExecutionContext에서_주입받은_동일한_baseTime을_사용한다")
    void 여러_격자를_처리해도_JobExecutionContext에서_주입받은_동일한_baseTime을_사용한다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 128);

      // when
      processor.process(grid1);
      processor.process(grid2);

      // then
      ArgumentCaptor<BaseTime> baseTimeCaptor = ArgumentCaptor.forClass(BaseTime.class);
      verify(weatherRefresher, times(2)).build(any(), any(), baseTimeCaptor.capture());
      List<BaseTime> capturedBaseTimes = baseTimeCaptor.getAllValues();
      assertThat(capturedBaseTimes.get(0)).isEqualTo(BASE_TIME);
      assertThat(capturedBaseTimes.get(1)).isEqualTo(BASE_TIME);
    }
  }
}