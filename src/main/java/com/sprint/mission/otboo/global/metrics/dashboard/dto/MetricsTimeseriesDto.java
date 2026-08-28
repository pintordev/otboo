package com.sprint.mission.otboo.global.metrics.dashboard.dto;

import java.util.List;

public record MetricsTimeseriesDto(
    List<MetricsDataPointDto> values
) {

}