package com.sprint.mission.otboo.batch.weatherfetch.config;

import com.sprint.mission.otboo.batch.weatherfetch.listener.WeatherFetchJobListener;
import com.sprint.mission.otboo.batch.weatherfetch.listener.WeatherFetchStepListener;
import com.sprint.mission.otboo.batch.weatherfetch.processor.WeatherFetchProcessor;
import com.sprint.mission.otboo.batch.weatherfetch.reader.WeatherFetchReader;
import com.sprint.mission.otboo.batch.weatherfetch.writer.WeatherFetchWriter;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import com.sprint.mission.otboo.global.batch.SkipLoggingListener;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class WeatherFetchJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final WeatherFetchJobListener weatherFetchJobListener;
  private final WeatherFetchStepListener weatherFetchStepListener;
  private final SkipLoggingListener skipLoggingListener;
  private final WeatherFetchReader weatherFetchReader;
  private final WeatherFetchProcessor weatherFetchProcessor;
  private final WeatherFetchWriter weatherFetchWriter;

  @Value("${batch.weather-fetch.chunk-size}")
  private int chunkSize;

  @Value("${batch.weather-fetch.skip-limit}")
  private int skipLimit;

  @Bean(name = "weatherFetchJob")
  public Job weatherFetchJob() {
    return new JobBuilder("weatherFetchJob", jobRepository)
        .listener(weatherFetchJobListener)
        .start(weatherFetchStep())
        .build();
  }

  @Bean
  public Step weatherFetchStep() {
    return new StepBuilder("weatherFetchStep", jobRepository)
        .<WeatherGrid, List<Weather>>chunk(chunkSize)
        .transactionManager(transactionManager)
        .reader(weatherFetchReader)
        .processor(weatherFetchProcessor)
        .writer(weatherFetchWriter)
        .faultTolerant()
        .skip(KmaApiException.class)
        .skipLimit(skipLimit)
        .skipListener(skipLoggingListener)
        .listener(weatherFetchStepListener)
        .build();
  }
}