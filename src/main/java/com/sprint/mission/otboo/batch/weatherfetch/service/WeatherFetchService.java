package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.batch.weatherfetch.exception.WeatherFetchJobFailedException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherFetchService {

  private final JobOperator jobOperator;

  @Qualifier("weatherFetchJob")
  private final Job weatherFetchJob;

  public void execute() {
    try {
      JobParameters params = new JobParametersBuilder()
          .addLong("time", Instant.now().toEpochMilli())
          .toJobParameters();
      jobOperator.start(weatherFetchJob, params);
    } catch (Exception e) {
      throw WeatherFetchJobFailedException.wrap(e);
    }
  }
}