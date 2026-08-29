package com.sprint.mission.otboo.global.metrics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.sprint.mission.otboo.global.metrics.dashboard.config.MetricsDashboardProperties;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsDataPointDto;
import com.sprint.mission.otboo.global.metrics.dashboard.dto.MetricsTimeseriesDto;
import com.sprint.mission.otboo.global.metrics.dashboard.exception.MetricsDashboardNotWhitelistedException;
import com.sprint.mission.otboo.global.metrics.dashboard.exception.MetricsDashboardQueryFailedException;
import com.sprint.mission.otboo.global.metrics.dashboard.filter.MetricsDashboardWhitelist;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsDashboardService")
class MetricsDashboardServiceTest {

  @InjectMocks
  MetricsDashboardService metricsDashboardService;

  @Mock
  CloudWatchClient cloudWatchClient;
  @Mock
  MetricsDashboardWhitelist whitelist;
  @Mock
  MetricsDashboardProperties properties;

  @Nested
  @DisplayName("시계열 조회")
  class GetTimeseries {

    @Test
    @DisplayName("화이트리스트에 없는 메트릭이면 CloudWatch를 호출하지 않고 예외를 던진다")
    void 화이트리스트에_없는_메트릭이면_예외를_던진다() {
      // given
      given(whitelist.matches("jvm.memory.used")).willReturn(false);

      // when & then
      assertThatThrownBy(() ->
          metricsDashboardService.getTimeseries("jvm.memory.used", MetricsRange.ONE_HOUR))
          .isInstanceOf(MetricsDashboardNotWhitelistedException.class);
      then(cloudWatchClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("화이트리스트에 있으면 GetMetricData 결과를 시계열 DTO로 변환한다")
    void 화이트리스트에_있으면_결과를_DTO로_변환한다() {
      // given
      String metric = "batch.weather-fetch.job.completed";
      given(whitelist.matches(metric)).willReturn(true);
      given(properties.namespace()).willReturn("otboo");

      Instant timestamp = Instant.parse("2026-08-28T00:00:00Z");
      MetricDataResult result = MetricDataResult.builder()
          .id("metric")
          .timestamps(timestamp)
          .values(3.0)
          .build();
      GetMetricDataResponse response = GetMetricDataResponse.builder()
          .metricDataResults(result)
          .build();
      given(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class)))
          .willReturn(response);

      // when
      MetricsTimeseriesDto actual = metricsDashboardService.getTimeseries(metric, MetricsRange.ONE_HOUR);

      // then
      assertThat(actual.values()).hasSize(1);
      assertThat(actual.values().get(0).timestamp()).isEqualTo(timestamp);
      assertThat(actual.values().get(0).value()).isEqualTo(3.0);

      ArgumentCaptor<GetMetricDataRequest> requestCaptor =
          ArgumentCaptor.forClass(GetMetricDataRequest.class);
      then(cloudWatchClient).should().getMetricData(requestCaptor.capture());
      GetMetricDataRequest request = requestCaptor.getValue();
      MetricStat metricStat = request.metricDataQueries().get(0).metricStat();
      assertThat(metricStat.metric().namespace()).isEqualTo("otboo");
      assertThat(metricStat.metric().metricName()).isEqualTo(metric);
      assertThat(metricStat.period()).isEqualTo((int) MetricsRange.ONE_HOUR.period().toSeconds());
      assertThat(Duration.between(request.startTime(), request.endTime()))
          .isEqualTo(MetricsRange.ONE_HOUR.lookback());
    }

    @Test
    @DisplayName("CloudWatch가 최신순으로 응답해도 timestamp 오름차순으로 정렬해서 반환한다")
    void CloudWatch가_최신순으로_응답해도_timestamp_오름차순으로_정렬해서_반환한다() {
      // given
      String metric = "batch.weather-fetch.job.completed";
      given(whitelist.matches(metric)).willReturn(true);
      given(properties.namespace()).willReturn("otboo");

      Instant older = Instant.parse("2026-08-28T00:00:00Z");
      Instant newer = Instant.parse("2026-08-28T01:00:00Z");
      // CloudWatch GetMetricData 기본 응답 순서(ScanBy 미지정 시 TimestampDescending)를
      // 재현 - 최신 타임스탬프가 배열 앞에 온다
      MetricDataResult result = MetricDataResult.builder()
          .id("metric")
          .timestamps(newer, older)
          .values(2.0, 1.0)
          .build();
      GetMetricDataResponse response = GetMetricDataResponse.builder()
          .metricDataResults(result)
          .build();
      given(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class)))
          .willReturn(response);

      // when
      MetricsTimeseriesDto actual = metricsDashboardService.getTimeseries(metric, MetricsRange.ONE_HOUR);

      // then
      assertThat(actual.values())
          .extracting(MetricsDataPointDto::timestamp)
          .containsExactly(older, newer);
    }
  }

  @Nested
  @DisplayName("CloudWatch 호출 실패")
  class CloudWatchFailure {

    @Test
    @DisplayName("CloudWatch 호출이 실패하면 MetricsDashboardQueryFailedException으로 감싼다")
    void CloudWatch_호출이_실패하면_예외로_감싼다() {
      // given
      String metric = "batch.weather-fetch.job.completed";
      given(whitelist.matches(metric)).willReturn(true);
      given(properties.namespace()).willReturn("otboo");
      CloudWatchException cause = (CloudWatchException) CloudWatchException.builder()
          .message("Rate exceeded")
          .build();
      given(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class))).willThrow(cause);

      // when
      MetricsDashboardQueryFailedException exception = catchThrowableOfType(
          MetricsDashboardQueryFailedException.class,
          () -> metricsDashboardService.getTimeseries(metric, MetricsRange.ONE_HOUR));

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(exception.getMessage()).isEqualTo("메트릭 조회에 실패했습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isEqualTo(cause);
    }
  }
}