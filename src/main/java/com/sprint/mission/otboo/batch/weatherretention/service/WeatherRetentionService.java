package com.sprint.mission.otboo.batch.weatherretention.service;

import com.sprint.mission.otboo.batch.weatherretention.exception.WeatherRetentionJobFailedException;
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
public class WeatherRetentionService {

  private final JobOperator jobOperator;

  @Qualifier("weatherRetentionJob")
  private final Job weatherRetentionJob;

  public void execute() {
    try {
      JobParameters params = new JobParametersBuilder()
          .addLong("time", Instant.now().toEpochMilli())
          .toJobParameters();
      jobOperator.start(weatherRetentionJob, params);
    } catch (Exception e) {
      throw WeatherRetentionJobFailedException.wrap(e);
    }
  }
}