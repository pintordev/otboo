package com.sprint.mission.otboo.batch.feedreindex.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexJobListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexSkipListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexStepListener;
import com.sprint.mission.otboo.batch.feedreindex.policy.FeedReindexSkipPolicy;
import com.sprint.mission.otboo.batch.feedreindex.reader.FeedIncrementalReindexReader;
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

@DisplayName("FeedReindexJobConfig")
class FeedReindexJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  private FeedReindexJobConfig config() {
    return new FeedReindexJobConfig(
        jobRepository,
        transactionManager,
        mock(FeedReindexJobListener.class),
        mock(FeedReindexStepListener.class),
        mock(FeedReindexSkipListener.class),
        mock(FeedReindexSkipPolicy.class),
        mock(FeedReindexReader.class),
        mock(FeedIncrementalReindexReader.class),
        mock(FeedReindexWriter.class),
        new FeedReindexProperties(500, 10, Duration.ofHours(2))
    );
  }

  @Nested
  @DisplayName("Job과 Step 생성")
  class JobAndStep {

    @Test
    @DisplayName("전체 재색인 Job과 Step을 생성한다")
    void 전체_재색인_Job과_Step을_생성한다() {
      // given
      FeedReindexJobConfig config = config();

      // when
      Job job = config.feedReindexJob();
      Step step = config.feedReindexStep();

      // then
      assertThat(job).isNotNull();
      assertThat(job.getName()).isEqualTo("feedReindexJob");
      assertThat(((AbstractJob) job).getStepNames()).containsExactly("feedReindexStep");

      assertThat(step).isNotNull();
      assertThat(step.getName()).isEqualTo("feedReindexStep");
    }

    @Test
    @DisplayName("증분 재색인 Job과 Step을 생성한다")
    void 증분_재색인_Job과_Step을_생성한다() {
      // given
      FeedReindexJobConfig config = config();

      // when
      Job job = config.feedIncrementalReindexJob();
      Step step = config.feedIncrementalReindexStep();

      // then
      assertThat(job).isNotNull();
      assertThat(job.getName()).isEqualTo("feedIncrementalReindexJob");
      assertThat(((AbstractJob) job).getStepNames())
          .containsExactly("feedIncrementalReindexStep");

      assertThat(step).isNotNull();
      assertThat(step.getName()).isEqualTo("feedIncrementalReindexStep");
    }

    @Test
    @DisplayName("증분 재색인 Job은 since 파라미터를 필수로 요구한다")
    void 증분_재색인_Job은_since_파라미터를_필수로_요구한다() {
      // given
      FeedReindexJobConfig config = config();
      Job job = config.feedIncrementalReindexJob();
      JobParameters withoutSince = new JobParametersBuilder()
          .addLong("time", Instant.now().toEpochMilli())
          .toJobParameters();

      // when & then
      assertThatThrownBy(() -> ((AbstractJob) job).getJobParametersValidator()
          .validate(withoutSince))
          .isInstanceOf(InvalidJobParametersException.class);
    }
  }
}
