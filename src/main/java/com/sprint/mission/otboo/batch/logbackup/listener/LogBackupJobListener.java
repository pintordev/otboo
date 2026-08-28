package com.sprint.mission.otboo.batch.logbackup.listener;

import com.sprint.mission.otboo.batch.logbackup.metrics.LogBackupMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
