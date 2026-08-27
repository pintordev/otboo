package com.sprint.mission.otboo.batch.orphanedimage.listener;

import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
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
public class OrphanedImageCleanupJobListener implements JobExecutionListener {

  private final OrphanedImageCleanupMetrics orphanedImageCleanupMetrics;

  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("유실 이미지 정리 Job 시작 | jobId={}, params={}", jobExecution.getId(),
        jobExecution.getJobParameters());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("유실 이미지 정리 Job 성공 | jobId={}", jobExecution.getId());
      orphanedImageCleanupMetrics.countCompleted();
    } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error("유실 이미지 정리 Job 실패 | jobId={}, exitStatus={}", jobExecution.getId(),
          jobExecution.getExitStatus());
      orphanedImageCleanupMetrics.countFailed();
    }

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      log.info("유실 이미지 정리 Job duration={}", duration);
      orphanedImageCleanupMetrics.recordJobDuration(duration);
    }

    if (!jobExecution.getAllFailureExceptions().isEmpty()) {
      jobExecution.getAllFailureExceptions()
          .forEach(e -> log.error("유실 이미지 정리 Job 실패 원인", e));
    }
  }
}
