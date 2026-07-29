package com.sprint.mission.otboo.batch.weatherfetch.exception;

public class WeatherFetchJobFailedException extends RuntimeException {

  private WeatherFetchJobFailedException(Throwable cause) {
    super("날씨 수집 배치 실행 실패", cause);
  }

  public static WeatherFetchJobFailedException wrap(Throwable cause) {
    return new WeatherFetchJobFailedException(cause);
  }
}