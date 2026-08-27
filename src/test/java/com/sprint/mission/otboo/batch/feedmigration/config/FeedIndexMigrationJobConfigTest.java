package com.sprint.mission.otboo.batch.feedmigration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexJobListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexSkipListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexStepListener;
import com.sprint.mission.otboo.batch.feedreindex.policy.FeedReindexSkipPolicy;
import com.sprint.mission.otboo.batch.feedreindex.reader.FeedReindexReader;
import com.sprint.mission.otboo.batch.feedreindex.writer.FeedReindexWriter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("FeedIndexMigrationJobConfig")
class FeedIndexMigrationJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  private FeedIndexMigrationJobConfig config() {
    return new FeedIndexMigrationJobConfig(
        jobRepository,
        transactionManager,
        mock(FeedReindexJobListener.class),
        mock(FeedReindexStepListener.class),
        mock(FeedReindexSkipListener.class),
        mock(FeedReindexSkipPolicy.class),
        mock(FeedReindexReader.class),
        mock(FeedReindexWriter.class),
        new FeedReindexProperties(500, 10, Duration.ofHours(2))
    );
  }

  @Nested
  @DisplayName("Job과 Step 생성")
  class JobAndStep {

    @Test
    @DisplayName("마이그레이션 Job과 Step을 생성한다")
    void 마이그레이션_Job과_Step을_생성한다() {
      // given
      FeedIndexMigrationJobConfig config = config();

      // when
      Job job = config.feedIndexMigrationJob();
      Step step = config.feedIndexMigrationStep();

      // then
      assertThat(job).isNotNull();
      assertThat(job.getName()).isEqualTo("feedIndexMigrationJob");
      assertThat(((AbstractJob) job).getStepNames())
          .containsExactly("feedIndexMigrationStep");

      assertThat(step).isNotNull();
      assertThat(step.getName()).isEqualTo("feedIndexMigrationStep");
    }

    @Test
    @DisplayName("마이그레이션 Job은 targetIndex 파라미터를 필수로 요구한다")
    void 마이그레이션_Job은_targetIndex_파라미터를_필수로_요구한다() {
      // given
      FeedIndexMigrationJobConfig config = config();
      Job job = config.feedIndexMigrationJob();
      JobParameters withoutTargetIndex = new JobParametersBuilder()
          .addLong("time", Instant.now().toEpochMilli())
          .toJobParameters();

      // when & then
      assertThatThrownBy(() -> ((AbstractJob) job).getJobParametersValidator()
          .validate(withoutTargetIndex))
          .isInstanceOf(InvalidJobParametersException.class);
    }
  }
}
