package com.sprint.mission.otboo.batch.logbackup.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class LogBackupMetrics {

  private static final String BACKUP = "batch.log-backup";
  private static final String BYTES = "batch.log-backup.bytes";
  private static final String JOB_COMPLETED = "batch.log-backup.job.completed";
  private static final String JOB_FAILED = "batch.log-backup.job.failed";
  private static final String JOB_DURATION = "batch.log-backup.job.duration";
  private static final String STEP_SKIPPED = "batch.log-backup.step.skipped";
  private static final String LAST_SUCCESS = "batch.log-backup.last_success.timestamp";

  private final MeterRegistry registry;
  private final Counter bytesCounter;
  private final Counter jobCompletedCounter;
  private final Counter jobFailedCounter;
  private final Timer jobDurationTimer;
  private final Counter stepSkippedCounter;
  private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0);

  public LogBackupMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.bytesCounter = Counter.builder(BYTES).baseUnit("bytes")
        .description("S3에 업로드된 압축 로그 총 바이트").register(registry);
    this.jobCompletedCounter = Counter.builder(JOB_COMPLETED)
        .description("LogBackup Job 성공 횟수").register(registry);
    this.jobFailedCounter = Counter.builder(JOB_FAILED)
        .description("LogBackup Job 실패 횟수").register(registry);
    this.jobDurationTimer = Timer.builder(JOB_DURATION)
        .description("LogBackup Job 실행 시간").register(registry);
    this.stepSkippedCounter = Counter.builder(STEP_SKIPPED)
        .description("LogBackup Step 스킵 건수").register(registry);
    Gauge.builder(LAST_SUCCESS, lastSuccessEpochSeconds, AtomicLong::get)
        .baseUnit("seconds")
        .description("로그 백업 배치가 마지막으로 정상 완료된 시각(epoch seconds)")
        .register(registry);
  }

  public void countUploaded() { backup("uploaded").increment(); }

  public void countSkipped() { backup("skipped").increment(); }

  public void countFailed() { backup("failed").increment(); }

  public void recordBytes(long bytes) { bytesCounter.increment(bytes); }

  public void countCompleted() { jobCompletedCounter.increment(); }

  public void countJobFailed() { jobFailedCounter.increment(); }

  public void recordJobDuration(Duration duration) { jobDurationTimer.record(duration); }

  public void countStepSkipped(long count) { stepSkippedCounter.increment(count); }

  public void markSuccess() { lastSuccessEpochSeconds.set(Instant.now().getEpochSecond()); }

  private Counter backup(String result) {
    return registry.counter(BACKUP, "result", result);
  }
}
