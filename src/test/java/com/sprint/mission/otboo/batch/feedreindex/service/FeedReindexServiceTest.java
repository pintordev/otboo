package com.sprint.mission.otboo.batch.feedreindex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.dto.FeedReindexResult;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexAlreadyRunningException;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexJobFailedException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.step.StepExecution;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedReindexService")
class FeedReindexServiceTest {

  private static final Duration LOOKBACK = Duration.ofHours(2);

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job feedReindexJob;

  @Mock
  private Job feedIncrementalReindexJob;

  @Mock
  private JobExecution jobExecution;

  private FeedReindexService feedReindexService;

  @BeforeEach
  void setUp() {
    feedReindexService = new FeedReindexService(
        jobOperator,
        new FeedReindexProperties(500, 10, LOOKBACK),
        feedReindexJob,
        feedIncrementalReindexJob);
  }

  @Nested
  @DisplayName("전체 재색인 실행")
  class ExecuteReindexAll {

    @Test
    @DisplayName("전체 재색인 Job을 JobParameters와 함께 실행한다")
    void 전체_재색인_Job을_JobParameters와_함께_실행한다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);

      // when
      feedReindexService.executeReindexAll();

      // then
      verify(jobOperator).start(eq(feedReindexJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("이미 실행 중이면 FeedReindexAlreadyRunningException을 던진다")
    void 이미_실행_중이면_FeedReindexAlreadyRunningException을_던진다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class)))
          .willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

      // when & then
      assertThatThrownBy(() -> feedReindexService.executeReindexAll())
          .isInstanceOf(FeedReindexAlreadyRunningException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
        JobRestartException.class,
        JobInstanceAlreadyCompleteException.class,
        InvalidJobParametersException.class})
    @DisplayName("이미 실행 중이 아닌 이유로 실패하면 FeedReindexJobFailedException으로 감싼다")
    void 이미_실행_중이_아닌_이유로_실패하면_FeedReindexJobFailedException으로_감싼다(
        Class<? extends Throwable> type) throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willThrow(type);

      // when & then
      assertThatThrownBy(() -> feedReindexService.executeReindexAll())
          .isInstanceOf(FeedReindexJobFailedException.class)
          .hasCauseInstanceOf(type);
    }

    @Test
    @DisplayName("Step 실행 결과를 집계해 반환한다")
    void Step_실행_결과를_집계해_반환한다() throws Exception {
      // given
      StepExecution stepExecution = mock(StepExecution.class);
      given(stepExecution.getReadCount()).willReturn(26L);
      given(stepExecution.getWriteCount()).willReturn(24L);
      given(jobExecution.getStepExecutions()).willReturn(List.of(stepExecution));
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);

      // when
      FeedReindexResult result = feedReindexService.executeReindexAll();

      // then
      assertThat(result.readCount()).isEqualTo(26L);
      assertThat(result.writeCount()).isEqualTo(24L);
    }
  }

  @Nested
  @DisplayName("증분 재색인 실행")
  class ExecuteIncrementalReindex {

    @Test
    @DisplayName("설정된 lookback 만큼 이전 시각을 기준으로 실행한다")
    void 설정된_lookback_만큼_이전_시각을_기준으로_실행한다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

      // when
      feedReindexService.executeIncrementalReindex();

      // then
      Instant after = Instant.now();
      ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(feedIncrementalReindexJob), captor.capture());

      Instant since = Instant.ofEpochMilli(captor.getValue().getLong("since"));
      assertThat(since)
          .isAfterOrEqualTo(before.minus(LOOKBACK))
          .isBeforeOrEqualTo(after.minus(LOOKBACK));
    }
  }
}
