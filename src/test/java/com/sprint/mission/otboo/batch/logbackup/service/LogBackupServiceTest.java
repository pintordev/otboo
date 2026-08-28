package com.sprint.mission.otboo.batch.logbackup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupJobFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class LogBackupServiceTest {

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job logBackupJob;

  @Mock
  private JobExecution jobExecution;

  private LogBackupService logBackupService;

  @BeforeEach
  void setUp() {
    logBackupService = new LogBackupService(jobOperator, logBackupJob);
  }

  @Nested
  @DisplayName("Execute")
  class Execute {

    @Test
    @DisplayName("logBackupJob을_JobParameters와_함께_실행한다")
    void logBackupJob을_JobParameters와_함께_실행한다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);

      // when
      logBackupService.executeBackup();

      // then
      verify(jobOperator).start(eq(logBackupJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("JobOperator_실행_실패시_LogBackupJobFailedException으로_감싸서_던진다")
    void JobOperator_실행_실패시_LogBackupJobFailedException으로_감싸서_던진다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class)))
          .willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

      // when & then
      assertThatThrownBy(() -> logBackupService.executeBackup())
          .isInstanceOf(LogBackupJobFailedException.class)
          .hasCauseInstanceOf(JobExecutionAlreadyRunningException.class);
    }

    @ParameterizedTest(name = "JobExecution 상태={0}")
    @EnumSource(value = BatchStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
    @DisplayName("JobExecution이_COMPLETED가_아니면_LogBackupJobFailedException을_던진다")
    void JobExecution이_COMPLETED가_아니면_LogBackupJobFailedException을_던진다(
        BatchStatus status) throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(status);

      // when & then
      assertThatThrownBy(() -> logBackupService.executeBackup())
          .isInstanceOf(LogBackupJobFailedException.class);
    }
  }
}
