package com.sprint.mission.otboo.batch.weatherfetch.listener;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherFetchJobListener implements JobExecutionListener {

  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("WeatherFetch Job 시작 | jobId={}, params={}", jobExecution.getId(),
        jobExecution.getJobParameters());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    // COMPLETED 분기는 향후 [FEAT] 날씨 급변 알림 트리거 이슈가 훅을 얹을 지점
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("WeatherFetch Job 성공 | jobId={}", jobExecution.getId());
    } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error("WeatherFetch Job 실패 | jobId={}, exitStatus={}", jobExecution.getId(),
          jobExecution.getExitStatus());
    }

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      log.info("WeatherFetch Job duration={}", duration);
    }

    if (!jobExecution.getAllFailureExceptions().isEmpty()) {
      jobExecution.getAllFailureExceptions()
          .forEach(e -> log.error("WeatherFetch Job 실패 원인", e));
    }
  }
}