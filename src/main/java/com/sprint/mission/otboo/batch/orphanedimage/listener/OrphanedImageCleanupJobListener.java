package com.sprint.mission.otboo.batch.orphanedimage.listener;

import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrphanedImageCleanupJobListener implements JobExecutionListener {

  private final OrphanedImageCleanupMetrics orphanedImageCleanupMetrics;

  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("유실 이미지 정리 Job 시작 | jobId={}, params={}", jobExecution.getId(),
        jobExecution.getJobParameters());
  }
}
