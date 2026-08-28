package com.sprint.mission.otboo.global.metrics.dashboard.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.global.metrics.dashboard.MetricsDashboardService;
import com.sprint.mission.otboo.global.metrics.dashboard.MetricsRange;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsTimeseriesDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsDashboardEndpoint")
class MetricsDashboardEndpointTest {

  @InjectMocks
  MetricsDashboardEndpoint endpoint;

  @Mock
  MetricsDashboardService metricsDashboardService;

  @Nested
  @DisplayName("시계열 조회")
  class Timeseries {

    @Test
    @DisplayName("서비스 결과를 그대로 반환한다")
    void 서비스_결과를_그대로_반환한다() {
      // given
      MetricsTimeseriesDto expected = new MetricsTimeseriesDto(List.of());
      given(metricsDashboardService.getTimeseries("batch.weather-fetch.job.completed", MetricsRange.ONE_HOUR))
          .willReturn(expected);

      // when
      MetricsTimeseriesDto actual =
          endpoint.timeseries("batch.weather-fetch.job.completed", MetricsRange.ONE_HOUR);

      // then
      assertThat(actual).isEqualTo(expected);
    }
  }
}