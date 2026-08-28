package com.sprint.mission.otboo.global.metrics.dashboard.endpoint;

import com.sprint.mission.otboo.global.metrics.dashboard.MetricsDashboardService;
import com.sprint.mission.otboo.global.metrics.dashboard.MetricsRange;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsTimeseriesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * 관리자 전용 메트릭 대시보드 시계열 조회.
 *
 * <p>{@code /actuator/**}는 ADMIN 권한이 걸려 있다(SecurityConfig).
 */
@Component
@RequiredArgsConstructor
@Endpoint(id = "metricsDashboard")
public class MetricsDashboardEndpoint {

  private final MetricsDashboardService metricsDashboardService;

  @ReadOperation
  public MetricsTimeseriesDto timeseries(String metric, String range) {
    return metricsDashboardService.getTimeseries(metric, MetricsRange.fromParam(range));
  }
}