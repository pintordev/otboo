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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

// weatherFetchStep과 weatherFetchRetryStep은 buildStep() 하나를 공유해 완전히 동일한 설정이고,
// 각 Step의 WeatherFetchReader는 @StepScope라 Step마다 새 인스턴스가 생성돼 커서가 매번
// Instant.EPOCH부터 다시 읽는다. 이때 findPageByCursorExcludingForecasted()의 "이미 이번
// baseTime으로 저장된 격자 제외" 필터가 유일한 안전장치다 - 이 필터 조건이 바뀌면
// weatherFetchRetryStep이 전 격자를 다시 조회하게 된다.
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({WeatherFetchProperties.class, WeatherChangeProperties.class})
public class WeatherFetchJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final WeatherFetchJobListener weatherFetchJobListener;
  private final WeatherFetchStepListener weatherFetchStepListener;
  private final SkipLoggingListener skipLoggingListener;
  private final WeatherFetchReader weatherFetchReader;
  private final WeatherFetchProcessor weatherFetchProcessor;
  private final WeatherFetchWriter weatherFetchWriter;
  private final WeatherFetchProperties weatherFetchProperties;

  @Bean(name = "weatherFetchJob")
  public Job weatherFetchJob() {
    return new JobBuilder("weatherFetchJob", jobRepository)
        .listener(weatherFetchJobListener)
        .start(weatherFetchStep())
        .on("*").to(weatherFetchRetryStep())
        .end()
        .build();
  }

  @Bean
  public Step weatherFetchStep() {
    return buildStep("weatherFetchStep");
  }

  @Bean
  public Step weatherFetchRetryStep() {
    return buildStep("weatherFetchRetryStep");
  }

  private Step buildStep(String stepName) {
    return new StepBuilder(stepName, jobRepository)
        .<WeatherGrid, List<Weather>>chunk(weatherFetchProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(weatherFetchReader)
        .processor(weatherFetchProcessor)
        .writer(weatherFetchWriter)
        .faultTolerant()
        .retryLimit(weatherFetchProperties.retryLimit())
        .retry(KmaApiException.class)
        .retry(TransientDataAccessException.class)
        .skip(KmaApiException.class)
        .skipLimit(weatherFetchProperties.skipLimit())
        .skipListener(skipLoggingListener)
        .listener(weatherFetchStepListener)
        .build();
  }
}