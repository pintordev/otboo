package com.sprint.mission.otboo.global.metrics.dashboard.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class MetricsDashboardQueryFailedException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.BAD_GATEWAY;
  private static final String MESSAGE = "메트릭 조회에 실패했습니다.";

  private MetricsDashboardQueryFailedException() {
    super(STATUS, MESSAGE, Map.of());
  }

  public static MetricsDashboardQueryFailedException wrap(Throwable cause) {
    MetricsDashboardQueryFailedException exception = new MetricsDashboardQueryFailedException();
    exception.initCause(cause);
    return exception;
  }
}