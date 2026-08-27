package com.sprint.mission.otboo.batch.orphanedimage.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OrphanedImageCleanupMetrics {

  private static final String COMPLETED = "batch.orphaned-image-cleanup.job.completed";
  private static final String FAILED = "batch.orphaned-image-cleanup.job.failed";
  private static final String JOB_DURATION = "batch.orphaned-image-cleanup.job.duration";
  private static final String DELETED = "batch.orphaned-image-cleanup.step.deleted";
  private static final String CAPPED = "batch.orphaned-image-cleanup.step.capped";

  private final Counter completedCounter;
  private final Counter failedCounter;
  private final Counter deletedCounter;
  private final Counter cappedCounter;
  private final Timer jobDurationTimer;

  public OrphanedImageCleanupMetrics(MeterRegistry registry) {
    this.completedCounter = Counter.builder(COMPLETED)
        .description("유실 이미지 삭제 Job 성공 횟수").register(registry);
    this.failedCounter = Counter.builder(FAILED)
        .description("유실 이미지 삭제 Job 실패 횟수").register(registry);
    this.deletedCounter = Counter.builder(DELETED)
        .description("삭제된 유실 이미지 총 건수").register(registry);
    this.cappedCounter = Counter.builder(CAPPED)
        .description("안전 상한에 걸려 삭제를 건너뛴 회차 수").register(registry);
    this.jobDurationTimer = Timer.builder(JOB_DURATION)
        .description("유실 이미지 삭제 Job 실행 시간").register(registry);
  }

  public void countDeleted(long count) { deletedCounter.increment(count); }
  public void countCapped() { cappedCounter.increment(); }
  public void countCompleted() { completedCounter.increment(); }
  public void countFailed() { failedCounter.increment(); }
  public void recordJobDuration(Duration duration) { jobDurationTimer.record(duration); }
}
