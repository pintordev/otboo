package com.sprint.mission.otboo.batch.feedreindex.config;

import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexJobListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexSkipListener;
import com.sprint.mission.otboo.batch.feedreindex.listener.FeedReindexStepListener;
import com.sprint.mission.otboo.batch.feedreindex.policy.FeedReindexSkipPolicy;
import com.sprint.mission.otboo.batch.feedreindex.reader.FeedIncrementalReindexReader;
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
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FeedReindexProperties.class)
public class FeedReindexJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final FeedReindexJobListener feedReindexJobListener;
  private final FeedReindexStepListener feedReindexStepListener;
  private final FeedReindexSkipListener feedReindexSkipListener;
  private final FeedReindexSkipPolicy feedReindexSkipPolicy;
  private final FeedReindexReader feedReindexReader;
  private final FeedIncrementalReindexReader feedIncrementalReindexReader;
  private final FeedReindexWriter feedReindexWriter;
  private final FeedReindexProperties feedReindexProperties;

  @Bean(name = "feedReindexJob")
  public Job feedReindexJob() {
    return new JobBuilder("feedReindexJob", jobRepository)
        .listener(feedReindexJobListener)
        .start(feedReindexStep())
        .build();
  }

  @Bean
  public Step feedReindexStep() {
    return buildStep("feedReindexStep", feedReindexReader);
  }

  @Bean(name = "feedIncrementalReindexJob")
  public Job feedIncrementalReindexJob() {
    return new JobBuilder("feedIncrementalReindexJob", jobRepository)
        .validator(incrementalJobParametersValidator())
        .listener(feedReindexJobListener)
        .start(feedIncrementalReindexStep())
        .build();
  }

  private JobParametersValidator incrementalJobParametersValidator() {
    DefaultJobParametersValidator validator = new DefaultJobParametersValidator();
    validator.setRequiredKeys(new String[]{"since"});
    return validator;
  }

  @Bean
  public Step feedIncrementalReindexStep() {
    return buildStep("feedIncrementalReindexStep", feedIncrementalReindexReader);
  }

  // 주 1회 안전망이라 문서 한 건의 실패가 남은 전부를 일주일 뒤로 미루면 안 된다.
  // 매핑 오류 같은 개별 문서 문제는 skip하고, 연결 실패는 그 시점의 모든 인덱싱이
  // 실패 중이라는 뜻이라 skip하지 않고 Step을 실패시킨다.
  // 예외를 나누는 기준은 FeedIndexEventListener와 같다.
  private Step buildStep(String stepName, ItemReader<Feed> reader) {
    return new StepBuilder(stepName, jobRepository)
        .<Feed, Feed>chunk(feedReindexProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(reader)
        .writer(feedReindexWriter)
        .faultTolerant()
        .skipPolicy(feedReindexSkipPolicy)
        .listener(feedReindexSkipListener)
        .listener(feedReindexStepListener)
        .build();
  }
}
