package com.sprint.mission.otboo.batch.weatherfetch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.weatherfetch.listener.WeatherFetchJobListener;
import com.sprint.mission.otboo.batch.weatherfetch.listener.WeatherFetchStepListener;
import com.sprint.mission.otboo.batch.weatherfetch.processor.WeatherFetchProcessor;
import com.sprint.mission.otboo.batch.weatherfetch.reader.WeatherFetchReader;
import com.sprint.mission.otboo.batch.weatherfetch.writer.WeatherFetchWriter;
import com.sprint.mission.otboo.global.batch.SkipLoggingListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class WeatherFetchJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager = mock(
      PlatformTransactionManager.class);

  @Nested
  @DisplayName("WeatherFetchJobConfig Job, Step 테스트")
  class JobStepTest {

    @Test
    @DisplayName("Job_Step_생성_성공")
    void Job_Step_생성_성공() {
      // given
      WeatherFetchJobListener jobListener = mock(WeatherFetchJobListener.class);
      WeatherFetchStepListener stepListener = mock(WeatherFetchStepListener.class);
      SkipLoggingListener skipLoggingListener = mock(SkipLoggingListener.class);
      WeatherFetchReader reader = mock(WeatherFetchReader.class);
      WeatherFetchProcessor processor = mock(WeatherFetchProcessor.class);
      WeatherFetchWriter writer = mock(WeatherFetchWriter.class);

      WeatherFetchJobConfig config = new WeatherFetchJobConfig(
          jobRepository,
          transactionManager,
          jobListener,
          stepListener,
          skipLoggingListener,
          reader,
          processor,
          writer,
          new WeatherFetchProperties(50, 10, 3)
      );

      // when
      Job job = config.weatherFetchJob();
      Step step = config.weatherFetchStep();
      Step retryStep = config.weatherFetchRetryStep();

      // then
      assertNotNull(job);
      assertThat(job.getName()).isEqualTo("weatherFetchJob");

      assertNotNull(step);
      assertThat(step.getName()).isEqualTo("weatherFetchStep");

      assertNotNull(retryStep);
      assertThat(retryStep.getName()).isEqualTo("weatherFetchRetryStep");
    }
  }
}