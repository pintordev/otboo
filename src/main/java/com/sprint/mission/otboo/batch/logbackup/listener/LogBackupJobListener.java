package com.sprint.mission.otboo.batch.logbackup.listener;

import com.sprint.mission.otboo.batch.logbackup.metrics.LogBackupMetrics;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LogBackupJobListener implements JobExecutionListener {

  private final LogBackupMetrics logBackupMetrics;

  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("LogBackup Job 시작 | jobId={}, params={}",
        jobExecution.getId(), jobExecution.getJobParameters());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("LogBackup Job 성공 | jobId={}", jobExecution.getId());
      logBackupMetrics.markSuccess();
      logBackupMetrics.countCompleted();
    } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error("LogBackup Job 실패 | jobId={}, exitStatus={}",
          jobExecution.getId(), jobExecution.getExitStatus());
      logBackupMetrics.countJobFailed();
    }

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      logBackupMetrics.recordJobDuration(duration);
      log.info("LogBackup Job duration={}", duration);
    } else {
      log.warn("LogBackup Job 시간 정보 누락 | start={}, end={}",
          jobExecution.getStartTime(), jobExecution.getEndTime());
    }

    if (!jobExecution.getAllFailureExceptions().isEmpty()) {
      jobExecution.getAllFailureExceptions().forEach(e -> log.error("LogBackup Job 실패 원인", e));
    }
  }
}
