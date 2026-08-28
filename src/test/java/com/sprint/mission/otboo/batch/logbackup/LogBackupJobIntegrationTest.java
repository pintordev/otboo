package com.sprint.mission.otboo.batch.logbackup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
class LogBackupJobIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  @Qualifier("logBackupJob")
  private Job logBackupJob;

  @MockitoBean
  private CloudWatchLogsClient cloudWatchLogsClient;
  @MockitoBean
  private S3Client s3Client;

  @BeforeEach
  void setUp() {
    jobOperatorTestUtils.setJob(logBackupJob);
  }

  @Nested
  @DisplayName("로그 백업 Job 실행")
  class RunJob {

    @Test
    @DisplayName("로그가_없으면_Step은_COMPLETED로_끝나고_아무것도_업로드하지_않는다")
    void 로그가_없으면_Step은_COMPLETED로_끝나고_아무것도_업로드하지_않는다() throws Exception {
      // given
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willReturn(FilterLogEventsResponse.builder().events(List.of()).nextToken(null).build());

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("로그_그룹_3개_중_1개가_이미_존재하면_2개만_업로드되고_1개는_스킵된다")
    void 로그_그룹_3개_중_1개가_이미_존재하면_2개만_업로드되고_1개는_스킵된다() throws Exception {
      // given — 설정된 로그 그룹 3개(app/nginx/es) 각각 하루치 단일 페이지를 반환
      given(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
          .willReturn(FilterLogEventsResponse.builder()
              .events(FilteredLogEvent.builder().message("line").build())
              .nextToken(null)
              .build());
      given(s3Client.headObject(any(Consumer.class)))
          .willReturn(HeadObjectResponse.builder().build()) // 1번째 그룹은 이미 존재
          .willThrow(NoSuchKeyException.builder().build())  // 나머지 2개는 신규
          .willThrow(NoSuchKeyException.builder().build());

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
  }
}
