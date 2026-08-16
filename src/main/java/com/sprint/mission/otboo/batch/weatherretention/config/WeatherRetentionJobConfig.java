package com.sprint.mission.otboo.batch.weatherretention.config;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionJobListener;
import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionStepListener;
import com.sprint.mission.otboo.batch.weatherretention.reader.WeatherD1BaselineRetentionReader;
import com.sprint.mission.otboo.batch.weatherretention.reader.WeatherRetentionReader;
import com.sprint.mission.otboo.batch.weatherretention.writer.WeatherD1BaselineRetentionWriter;
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
  private final WeatherD1BaselineRetentionReader weatherD1BaselineRetentionReader;
  private final WeatherD1BaselineRetentionWriter weatherD1BaselineRetentionWriter;
  private final WeatherRetentionProperties weatherRetentionProperties;

  @Bean(name = "weatherRetentionJob")
  public Job weatherRetentionJob() {
    return new JobBuilder("weatherRetentionJob", jobRepository)
        .listener(weatherRetentionJobListener)
        .start(weatherRetentionStep())
        .next(weatherD1BaselineRetentionStep())
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

  // weathers 리텐션과 별개 테이블(weather_d1_baselines) 정리지만, target_date가 지난 고아 baseline도
  // 결국 "오래된 날씨 데이터 정리"라는 같은 배치 관심사라 별도 Job 대신 같은 Job의 Step으로 묶는다(#163).
  @Bean
  public Step weatherD1BaselineRetentionStep() {
    return new StepBuilder("weatherD1BaselineRetentionStep", jobRepository)
        .<WeatherD1BaselineRetentionItem, WeatherD1BaselineRetentionItem>chunk(
            weatherRetentionProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(weatherD1BaselineRetentionReader)
        .writer(weatherD1BaselineRetentionWriter)
        .listener(weatherRetentionStepListener)
        .build();
  }
}