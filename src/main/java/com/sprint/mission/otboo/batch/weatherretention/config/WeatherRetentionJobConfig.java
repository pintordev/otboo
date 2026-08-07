package com.sprint.mission.otboo.batch.weatherretention.config;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionJobListener;
import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionStepListener;
import com.sprint.mission.otboo.batch.weatherretention.reader.WeatherRetentionReader;
import com.sprint.mission.otboo.batch.weatherretention.writer.WeatherRetentionWriter;
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
@EnableConfigurationProperties(WeatherRetentionProperties.class)
public class WeatherRetentionJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final WeatherRetentionJobListener weatherRetentionJobListener;
  private final WeatherRetentionStepListener weatherRetentionStepListener;
  private final WeatherRetentionReader weatherRetentionReader;
  private final WeatherRetentionWriter weatherRetentionWriter;
  private final WeatherRetentionProperties weatherRetentionProperties;

  @Bean(name = "weatherRetentionJob")
  public Job weatherRetentionJob() {
    return new JobBuilder("weatherRetentionJob", jobRepository)
        .listener(weatherRetentionJobListener)
        .start(weatherRetentionStep())
        .build();
  }

  @Bean
  public Step weatherRetentionStep() {
    return new StepBuilder("weatherRetentionStep", jobRepository)
        .<WeatherRetentionItem, WeatherRetentionItem>chunk(weatherRetentionProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(weatherRetentionReader)
        .writer(weatherRetentionWriter)
        .listener(weatherRetentionStepListener)
        .build();
  }
}