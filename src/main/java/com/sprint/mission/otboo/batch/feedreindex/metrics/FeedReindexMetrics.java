package com.sprint.mission.otboo.batch.feedreindex.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class FeedReindexMetrics {

  private static final String COMPLETED = "batch.feed-reindex.job.completed";
  private static final String FAILED = "batch.feed-reindex.job.failed";
  private static final String JOB_DURATION = "batch.feed-reindex.job.duration";
  private static final String REINDEXED = "batch.feed-reindex.step.reindexed";
  private static final String STEP = "step";
  private static final String DRIFT = "batch.feed-reindex.step.drift";

  private final MeterRegistry registry;
  private final Counter completedCounter;
  private final Counter failedCounter;
  private final Timer jobDurationTimer;

  public FeedReindexMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.completedCounter = Counter.builder(COMPLETED)
        .description("FeedReindex Job 성공 횟수")
        .register(registry);
    this.failedCounter = Counter.builder(FAILED)
        .description("FeedReindex Job 실패 횟수")
        .register(registry);
    this.jobDurationTimer = Timer.builder(JOB_DURATION)
        .description("FeedReindex Job 실행 시간")
        .register(registry);
  }

  public void countCompleted() {
    completedCounter.increment();
  }

  public void countFailed() {
    failedCounter.increment();
  }

  public void recordJobDuration(Duration duration) {
    jobDurationTimer.record(duration);
  }

  public void countReindexed(String stepName, long count) {
    registry.counter(REINDEXED, STEP, stepName).increment(count);
  }

  public void countDrift(String stepName, long count) {
    registry.counter(DRIFT, STEP, stepName).increment(count);
  }
}
