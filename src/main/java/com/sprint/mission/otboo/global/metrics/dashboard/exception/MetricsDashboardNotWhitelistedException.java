package com.sprint.mission.otboo.global.metrics.dashboard.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class MetricsDashboardNotWhitelistedException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
  private static final String MESSAGE = "화이트리스트에 없는 메트릭입니다.";

  private MetricsDashboardNotWhitelistedException(Map<String, Object> details) {
    super(STATUS, MESSAGE, details);
  }

  public static MetricsDashboardNotWhitelistedException withMetric(String metric) {
    return new MetricsDashboardNotWhitelistedException(Map.of("metric", metric));
  }
}