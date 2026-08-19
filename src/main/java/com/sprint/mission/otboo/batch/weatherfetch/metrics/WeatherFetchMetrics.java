package com.sprint.mission.otboo.batch.weatherfetch.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WeatherFetchMetrics {

  private static final String COMPLETED = "batch.weather-fetch.job.completed";
  private static final String FAILED = "batch.weather-fetch.job.failed";
  private static final String JOB_DURATION = "batch.weather-fetch.job.duration";
  private static final String SKIPPED = "batch.weather-fetch.step.skipped";
  private static final String NOTIFIED = "batch.weather-fetch.notified";
  private static final String TYPE = "type";

  private final MeterRegistry registry;
  private final Counter completedCounter;
  private final Counter failedCounter;
  private final Timer jobDurationTimer;
  private final Counter skippedCounter;

  public WeatherFetchMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.completedCounter = Counter.builder(COMPLETED)
        .description("WeatherFetch Job 성공 횟수")
        .register(registry);
    this.failedCounter = Counter.builder(FAILED)
        .description("WeatherFetch Job 실패 횟수")
        .register(registry);
    this.jobDurationTimer = Timer.builder(JOB_DURATION)
        .description("WeatherFetch Job 실행 시간")
        .register(registry);
    this.skippedCounter = Counter.builder(SKIPPED)
        .description("WeatherFetch Step 스킵 건수")
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

  public void countSkipped(long count) {
    skippedCounter.increment(count);
  }

  public void countNotified(String type, int count) {
    registry.counter(NOTIFIED, TYPE, type).increment(count);
  }
}