package com.sprint.mission.otboo.batch.weatherretention.listener;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherRetentionJobListener implements JobExecutionListener {

  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("WeatherRetention Job 시작 | jobId={}, params={}", jobExecution.getId(),
        jobExecution.getJobParameters());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("WeatherRetention Job 성공 | jobId={}", jobExecution.getId());
    } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error("WeatherRetention Job 실패 | jobId={}, exitStatus={}", jobExecution.getId(),
          jobExecution.getExitStatus());
    }

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      log.info("WeatherRetention Job duration={}", duration);
    }

    if (!jobExecution.getAllFailureExceptions().isEmpty()) {
      jobExecution.getAllFailureExceptions()
          .forEach(e -> log.error("WeatherRetention Job 실패 원인", e));
    }
  }
}