package com.sprint.mission.otboo.batch.logbackup.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties;
import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties.LogGroupTarget;
import com.sprint.mission.otboo.batch.logbackup.dto.LogContent;
import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupReadFailedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

@ExtendWith(MockitoExtension.class)
class LogBackupReaderTest {

  @Mock
  private CloudWatchLogsClient cloudWatchLogsClient;

  private LogBackupReader reader;

  @BeforeEach
  void setUp() {
    reader = new LogBackupReader(cloudWatchLogsClient, properties(
        List.of(new LogGroupTarget("/ecs/otboo", "app")), 1));
  }

  private LogBackupProperties properties(List<LogGroupTarget> logGroups, int lookbackDays) {
    return new LogBackupProperties(logGroups, "otboo-backup", 10, 5, 3, lookbackDays);
  }

  @Nested
  @DisplayName("페이지 조회")
  class Read {

    @Test
    @DisplayName("첫_호출에서_로그_라인을_모아_하나의_LogContent로_반환한다")
    void 첫_호출에서_로그_라인을_모아_하나의_LogContent로_반환한다() {
      // given
      FilteredLogEvent event1 = FilteredLogEvent.builder().message("line1").build();
      FilteredLogEvent event2 = FilteredLogEvent.builder().message("line2").build();
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willReturn(FilterLogEventsResponse.builder()
              .events(event1, event2)
              .nextToken(null)
              .build());

      // when
      LogContent content = reader.read();

      // then
      assertThat(content).isNotNull();
      assertThat(content.groupLabel()).isEqualTo("app");
      assertThat(new String(content.lines(), StandardCharsets.UTF_8)).isEqualTo("line1\nline2");
      assertThat(content.pageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("lookbackDays가_1이면_하루치를_다_읽은_후_null을_반환한다")
    void lookbackDays가_1이면_하루치를_다_읽은_후_null을_반환한다() {
      // given
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willReturn(FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("line1").build())
              .nextToken(null)
              .build());

      // when
      reader.read();
      LogContent second = reader.read();

      // then
      assertThat(second).isNull();
    }

    @Test
    @DisplayName("빈_페이지는_건너뛰고_다음_페이지를_계속_조회한다")
    void 빈_페이지는_건너뛰고_다음_페이지를_계속_조회한다() {
      // given — 첫 페이지는 이벤트 없이 nextToken만 있고, 두 번째 페이지에 실제 로그가 있다
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willReturn(
              FilterLogEventsResponse.builder().events(List.of()).nextToken("token-1").build(),
              FilterLogEventsResponse.builder()
                  .events(FilteredLogEvent.builder().message("line1").build())
                  .nextToken(null)
                  .build());

      // when
      LogContent content = reader.read();

      // then
      assertThat(content).isNotNull();
      verify(cloudWatchLogsClient, times(2)).filterLogEvents(any(FilterLogEventsRequest.class));
    }

    @Test
    @DisplayName("lookbackDays가_2이면_옛날_날짜부터_순서대로_순회하고_다_읽으면_null을_반환한다")
    void lookbackDays가_2이면_옛날_날짜부터_순서대로_순회하고_다_읽으면_null을_반환한다() {
      // given
      reader = new LogBackupReader(cloudWatchLogsClient, properties(
          List.of(new LogGroupTarget("/ecs/otboo", "app")), 2));
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class))).willReturn(
          FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("day1").build())
              .nextToken(null)
              .build(),
          FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("day2").build())
              .nextToken(null)
              .build());

      // when
      LogContent first = reader.read();
      LogContent second = reader.read();
      LogContent third = reader.read();

      // then — 날짜는 오래된 쪽부터 순회하고, 두 날짜를 다 읽으면 null
      assertThat(first.date()).isBefore(second.date());
      assertThat(third).isNull();
    }

    @Test
    @DisplayName("로그_그룹이_여러_개면_그룹별로_순서대로_순회한다")
    void 로그_그룹이_여러_개면_그룹별로_순서대로_순회한다() {
      // given — app 그룹을 하루치 다 읽은 뒤에야 nginx 그룹으로 넘어가야 한다
      reader = new LogBackupReader(cloudWatchLogsClient, properties(
          List.of(new LogGroupTarget("/ecs/otboo", "app"),
              new LogGroupTarget("/ecs/otboo-nginx", "nginx")), 1));
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class))).willReturn(
          FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("app-line").build())
              .nextToken(null)
              .build(),
          FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("nginx-line").build())
              .nextToken(null)
              .build());

      // when
      LogContent first = reader.read();
      LogContent second = reader.read();
      LogContent third = reader.read();

      // then
      assertThat(first.groupLabel()).isEqualTo("app");
      assertThat(second.groupLabel()).isEqualTo("nginx");
      assertThat(third).isNull();
    }

    @Test
    @DisplayName("CloudWatch_호출이_실패하면_LogBackupReadFailedException으로_감싸_던진다")
    void CloudWatch_호출이_실패하면_LogBackupReadFailedException으로_감싸_던진다() {
      // given
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willThrow(SdkException.builder().message("throttled").build());

      // when & then
      assertThatThrownBy(() -> reader.read())
          .isInstanceOf(LogBackupReadFailedException.class);
    }
  }
}
