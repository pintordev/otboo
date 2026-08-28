package com.sprint.mission.otboo.global.metrics.dashboard;

import com.sprint.mission.otboo.global.metrics.dashboard.config.MetricsDashboardProperties;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsDataPointDto;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsTimeseriesDto;
import com.sprint.mission.otboo.global.metrics.dashboard.exception.MetricsDashboardNotWhitelistedException;
import com.sprint.mission.otboo.global.metrics.dashboard.filter.MetricsDashboardWhitelist;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;

@Service
@RequiredArgsConstructor
public class MetricsDashboardService {

  private static final String STAT = "Average";
  private static final String QUERY_ID = "metric";

  private final CloudWatchClient cloudWatchClient;
  private final MetricsDashboardWhitelist whitelist;
  private final MetricsDashboardProperties properties;

  public MetricsTimeseriesDto getTimeseries(String metric, MetricsRange range) {
    if (!whitelist.matches(metric)) {
      throw MetricsDashboardNotWhitelistedException.withMetric(metric);
    }

    GetMetricDataResponse response = cloudWatchClient.getMetricData(buildRequest(metric, range));

    List<MetricsDataPointDto> values = response.metricDataResults().stream()
        .flatMap(result -> toDataPoints(result).stream())
        .toList();

    return new MetricsTimeseriesDto(values);
  }

  private GetMetricDataRequest buildRequest(String metric, MetricsRange range) {
    Instant end = Instant.now();
    Instant start = end.minus(range.lookback());

    Metric cwMetric = Metric.builder()
        .namespace(properties.namespace())
        .metricName(metric)
        .build();

    MetricStat metricStat = MetricStat.builder()
        .metric(cwMetric)
        .period((int) range.period().toSeconds())
        .stat(STAT)
        .build();

    MetricDataQuery query = MetricDataQuery.builder()
        .id(QUERY_ID)
        .metricStat(metricStat)
        .build();

    return GetMetricDataRequest.builder()
        .startTime(start)
        .endTime(end)
        .metricDataQueries(query)
        .build();
  }

  private List<MetricsDataPointDto> toDataPoints(MetricDataResult result) {
    List<Instant> timestamps = result.timestamps();
    List<Double> values = result.values();
    List<MetricsDataPointDto> points = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      points.add(new MetricsDataPointDto(timestamps.get(i), values.get(i)));
    }
    return points;
  }
}