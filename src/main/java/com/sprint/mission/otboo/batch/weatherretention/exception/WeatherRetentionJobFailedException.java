package com.sprint.mission.otboo.batch.weatherretention.exception;

public class WeatherRetentionJobFailedException extends RuntimeException {

  private WeatherRetentionJobFailedException(Throwable cause) {
    super("날씨 retention 배치 실행 실패", cause);
  }

  public static WeatherRetentionJobFailedException wrap(Throwable cause) {
    return new WeatherRetentionJobFailedException(cause);
  }
}