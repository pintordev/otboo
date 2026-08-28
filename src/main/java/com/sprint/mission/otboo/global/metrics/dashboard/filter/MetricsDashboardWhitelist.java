package com.sprint.mission.otboo.global.metrics.dashboard.filter;

import java.util.List;

public class MetricsDashboardWhitelist {

  private final List<String> prefixes;

  public MetricsDashboardWhitelist(List<String> prefixes) {
    this.prefixes = prefixes;
  }

  public boolean matches(String meterName) {
    return prefixes.stream().anyMatch(meterName::startsWith);
  }
}