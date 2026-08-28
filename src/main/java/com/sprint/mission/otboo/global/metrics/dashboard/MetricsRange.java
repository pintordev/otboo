package com.sprint.mission.otboo.global.metrics.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

public enum MetricsRange {

  @JsonProperty("1h")
  ONE_HOUR("1h", Duration.ofHours(1), Duration.ofSeconds(60)),

  @JsonProperty("6h")
  SIX_HOURS("6h", Duration.ofHours(6), Duration.ofSeconds(60)),

  @JsonProperty("12h")
  TWELVE_HOURS("12h", Duration.ofHours(12), Duration.ofSeconds(300)),

  @JsonProperty("24h")
  ONE_DAY("24h", Duration.ofHours(24), Duration.ofSeconds(300)),

  @JsonProperty("7d")
  SEVEN_DAYS("7d", Duration.ofDays(7), Duration.ofSeconds(3600)),

  @JsonProperty("14d")
  FOURTEEN_DAYS("14d", Duration.ofDays(14), Duration.ofSeconds(3600));

  private final String param;
  private final Duration lookback;
  private final Duration period;

  MetricsRange(String param, Duration lookback, Duration period) {
    this.param = param;
    this.lookback = lookback;
    this.period = period;
  }

  public String param() {
    return param;
  }

  public Duration lookback() {
    return lookback;
  }

  public Duration period() {
    return period;
  }

  public static MetricsRange fromParam(String param) {
    return switch (param) {
      case "1h" -> ONE_HOUR;
      case "12h" -> TWELVE_HOURS;
      case "24h" -> ONE_DAY;
      case "7d" -> SEVEN_DAYS;
      case "14d" -> FOURTEEN_DAYS;
      default -> SIX_HOURS;
    };
  }
}