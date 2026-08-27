package com.sprint.mission.otboo.batch.feedmigration.config;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexJobListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexSkipListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexStepListener;
import com.sprint.mission.otboo.batch.feedreindex.policy.FeedReindexSkipPolicy;
import com.sprint.mission.otboo.batch.feedreindex.reader.FeedReindexReader;
import com.sprint.mission.otboo.batch.feedreindex.writer.FeedReindexWriter;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 매핑 마이그레이션 시 새 인덱스로 전체 재색인하는 Job.
 *
 * <p>Reader·Writer·정책·리스너는 재색인 배치와 같고 대상 인덱스만 다르다. 그럼에도 별도 Job으로
 * 두는 것은, 주 1회 자동으로 도는 재색인과 사람이 트리거하는 마이그레이션이 같은 이름으로 실행되면 로그·메트릭·실행 이력에서 구분되지 않기 때문이다.
 */
@Configuration
@RequiredArgsConstructor
public class FeedIndexMigrationJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final FeedReindexJobListener feedReindexJobListener;
  private final FeedReindexStepListener feedReindexStepListener;
  private final FeedReindexSkipListener feedReindexSkipListener;
  private final FeedReindexSkipPolicy feedReindexSkipPolicy;
  private final FeedReindexReader feedReindexReader;
  private final FeedReindexWriter feedReindexWriter;
  private final FeedReindexProperties feedReindexProperties;

  @Bean(name = "feedIndexMigrationJob")
  public Job feedIndexMigrationJob() {
    return new JobBuilder("feedIndexMigrationJob", jobRepository)
        .validator(migrationJobParametersValidator())
        .listener(feedReindexJobListener)
        .start(feedIndexMigrationStep())
        .build();
  }

  // 대상 인덱스가 없으면 alias(feeds)에 그대로 써 버려 전환 전 인덱스를 오염시킨다.
  private JobParametersValidator migrationJobParametersValidator() {
    DefaultJobParametersValidator validator = new DefaultJobParametersValidator();
    validator.setRequiredKeys(new String[]{"targetIndex"});
    return validator;
  }

  @Bean
  public Step feedIndexMigrationStep() {
    return new StepBuilder("feedIndexMigrationStep", jobRepository)
        .<Feed, Feed>chunk(feedReindexProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(feedReindexReader)
        .writer(feedReindexWriter)
        .faultTolerant()
        .skipPolicy(feedReindexSkipPolicy)
        .listener(feedReindexSkipListener)
        .listener(feedReindexStepListener)
        .build();
  }
}
