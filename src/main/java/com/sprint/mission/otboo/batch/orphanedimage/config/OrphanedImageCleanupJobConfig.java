package com.sprint.mission.otboo.batch.orphanedimage.config;

import com.sprint.mission.otboo.batch.orphanedimage.listener.OrphanedImageCleanupJobListener;
import com.sprint.mission.otboo.batch.orphanedimage.reader.OrphanedImageReader;
import com.sprint.mission.otboo.batch.orphanedimage.writer.OrphanedImageWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OrphanedImageCleanupProperties.class)
public class OrphanedImageCleanupJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final OrphanedImageCleanupJobListener orphanedImageCleanupJobListener;
  private final OrphanedImageReader orphanedImageReader;
  private final OrphanedImageWriter orphanedImageWriter;
  private final OrphanedImageCleanupProperties properties;

  @Bean(name = "orphanedImageCleanupJob")
  public Job orphanedImageCleanupJob() {
    return new JobBuilder("orphanedImageCleanupJob", jobRepository)
        .listener(orphanedImageCleanupJobListener)
        .start(orphanedImageCleanupStep())
        .build();
  }

  @Bean
  public Step orphanedImageCleanupStep() {
    return new StepBuilder("orphanedImageCleanupStep", jobRepository)
        .<String, String>chunk(properties.chunkSize())
        .transactionManager(transactionManager)
        .reader(orphanedImageReader)
        .writer(orphanedImageWriter)
        .build();
  }
}
