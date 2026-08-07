package com.sprint.mission.otboo.batch.weatherretention.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherretention.exception.WeatherRetentionJobFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class WeatherRetentionServiceTest {

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job weatherRetentionJob;

  private WeatherRetentionService weatherRetentionService;

  @BeforeEach
  void setUp() {
    weatherRetentionService = new WeatherRetentionService(jobOperator, weatherRetentionJob);
  }

  @Nested
  @DisplayName("Execute")
  class Execute {

    @Test
    @DisplayName("weatherRetentionJob을_JobParameters와_함께_실행한다")
    void weatherRetentionJob을_JobParameters와_함께_실행한다() throws Exception {
      // when
      weatherRetentionService.execute();

      // then
      verify(jobOperator).start(eq(weatherRetentionJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("JobOperator_실행_실패시_WeatherRetentionJobFailedException으로_감싸서_던진다")
    void JobOperator_실행_실패시_WeatherRetentionJobFailedException으로_감싸서_던진다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class)))
          .willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

      // when & then
      assertThatThrownBy(() -> weatherRetentionService.execute())
          .isInstanceOf(WeatherRetentionJobFailedException.class)
          .hasCauseInstanceOf(JobExecutionAlreadyRunningException.class);
    }
  }
}