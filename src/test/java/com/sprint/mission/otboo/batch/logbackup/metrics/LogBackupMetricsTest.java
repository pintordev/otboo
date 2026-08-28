package com.sprint.mission.otboo.batch.logbackup.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LogBackupMetricsTest {

  @Nested
  @DisplayName("계측")
  class Metrics {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final LogBackupMetrics metrics = new LogBackupMetrics(registry);

    @Test
    @DisplayName("업로드_스킵_실패_건수가_각각_다른_result_태그로_집계된다")
    void 업로드_스킵_실패_건수가_각각_다른_result_태그로_집계된다() {
      // when
      metrics.countUploaded();
      metrics.countUploaded();
      metrics.countSkipped();
      metrics.countFailed();

      // then
      assertThat(registry.counter("batch.log-backup", "result", "uploaded").count()).isEqualTo(2);
      assertThat(registry.counter("batch.log-backup", "result", "skipped").count()).isEqualTo(1);
      assertThat(registry.counter("batch.log-backup", "result", "failed").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("markSuccess_호출_시각이_Gauge에_epoch_seconds로_반영된다")
    void markSuccess_호출_시각이_Gauge에_epoch_seconds로_반영된다() {
      // given
      long before = Instant.now().getEpochSecond();

      // when
      metrics.markSuccess();

      // then
      Gauge gauge = registry.find("batch.log-backup.last_success.timestamp").gauge();
      assertThat(gauge.value()).isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("countCompleted_countJobFailed가_각각_다른_메트릭_이름으로_집계된다")
    void countCompleted_countJobFailed가_각각_다른_메트릭_이름으로_집계된다() {
      // when
      metrics.countCompleted();
      metrics.countCompleted();
      metrics.countJobFailed();

      // then
      assertThat(registry.counter("batch.log-backup.job.completed").count()).isEqualTo(2);
      assertThat(registry.counter("batch.log-backup.job.failed").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("countStepSkipped_호출_시_batch_log_backup_step_skipped에_반영된다")
    void countStepSkipped_호출_시_batch_log_backup_step_skipped에_반영된다() {
      // when
      metrics.countStepSkipped(3);

      // then
      assertThat(registry.counter("batch.log-backup.step.skipped").count()).isEqualTo(3);
    }
  }
}
