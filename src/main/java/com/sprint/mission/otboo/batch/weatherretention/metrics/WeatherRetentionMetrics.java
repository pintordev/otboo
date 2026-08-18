package com.sprint.mission.otboo.batch.weatherretention.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WeatherRetentionMetrics {

  private static final String COMPLETED = "batch.weather-retention.job.completed";
  private static final String FAILED = "batch.weather-retention.job.failed";
  private static final String JOB_DURATION = "batch.weather-retention.job.duration";
  private static final String CLEANED = "batch.weather-retention.step.cleaned";

  private final Counter completedCounter;
  private final Counter failedCounter;
  private final Timer jobDurationTimer;
  private final Counter cleanedCounter;

  public WeatherRetentionMetrics(MeterRegistry registry) {
    this.completedCounter = Counter.builder(COMPLETED)
        .description("WeatherRetention Job 성공 횟수")
        .register(registry);
    this.failedCounter = Counter.builder(FAILED)
        .description("WeatherRetention Job 실패 횟수")
        .register(registry);
    this.jobDurationTimer = Timer.builder(JOB_DURATION)
        .description("WeatherRetention Job 실행 시간")
        .register(registry);
    this.cleanedCounter = Counter.builder(CLEANED)
        .description("WeatherRetention Step 정리(삭제) 건수")
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

  public void countCleaned(long count) {
    cleanedCounter.increment(count);
  }
}