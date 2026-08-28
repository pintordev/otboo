package com.sprint.mission.otboo.global.metrics.dashboard.dto;

import java.time.Instant;

public record MetricsDataPointDto(
    Instant timestamp,
    double value
) {

}