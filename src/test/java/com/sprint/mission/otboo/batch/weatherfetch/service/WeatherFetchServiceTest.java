package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherfetch.exception.WeatherFetchJobFailedException;
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
class WeatherFetchServiceTest {

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job weatherFetchJob;

  private WeatherFetchService weatherFetchService;

  @BeforeEach
  void setUp() {
    weatherFetchService = new WeatherFetchService(jobOperator, weatherFetchJob);
  }

  @Nested
  @DisplayName("Execute")
  class Execute {

    @Test
    @DisplayName("weatherFetchJob을_JobParameters와_함께_실행한다")
    void weatherFetchJob을_JobParameters와_함께_실행한다() throws Exception {
      // when
      weatherFetchService.execute();

      // then
      verify(jobOperator).start(eq(weatherFetchJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("JobOperator_실행_실패시_WeatherFetchJobFailedException으로_감싸서_던진다")
    void JobOperator_실행_실패시_WeatherFetchJobFailedException으로_감싸서_던진다() throws Exception {
      // given
      given(jobOperator.start(any(Job.class), any(JobParameters.class)))
          .willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"));

      // when & then
      assertThatThrownBy(() -> weatherFetchService.execute())
          .isInstanceOf(WeatherFetchJobFailedException.class)
          .hasCauseInstanceOf(JobExecutionAlreadyRunningException.class);
    }
  }
}