package com.sprint.mission.otboo.batch.weatherretention.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionJobListener;
import com.sprint.mission.otboo.batch.weatherretention.listener.WeatherRetentionStepListener;
import com.sprint.mission.otboo.batch.weatherretention.reader.WeatherRetentionReader;
import com.sprint.mission.otboo.batch.weatherretention.writer.WeatherRetentionWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class WeatherRetentionJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager = mock(
      PlatformTransactionManager.class);

  @Nested
  @DisplayName("WeatherRetentionJobConfig Job, Step 테스트")
  class JobStepTest {

    @Test
    @DisplayName("Job_Step_생성_성공")
    void Job_Step_생성_성공() {
      // given
      WeatherRetentionJobListener jobListener = mock(WeatherRetentionJobListener.class);
      WeatherRetentionStepListener stepListener = mock(WeatherRetentionStepListener.class);
      WeatherRetentionReader reader = mock(WeatherRetentionReader.class);
      WeatherRetentionWriter writer = mock(WeatherRetentionWriter.class);

      WeatherRetentionJobConfig config = new WeatherRetentionJobConfig(
          jobRepository,
          transactionManager,
          jobListener,
          stepListener,
          reader,
          writer,
          new WeatherRetentionProperties(500, 7)
      );

      // when
      Job job = config.weatherRetentionJob();
      Step step = config.weatherRetentionStep();

      // then
      assertNotNull(job);
      assertThat(job.getName()).isEqualTo("weatherRetentionJob");

      assertNotNull(step);
      assertThat(step.getName()).isEqualTo("weatherRetentionStep");
    }
  }
}