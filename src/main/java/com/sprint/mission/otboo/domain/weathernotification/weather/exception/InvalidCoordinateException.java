package com.sprint.mission.otboo.domain.weathernotification.weather.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidCoordinateException extends WeatherException {

  private static final String MESSAGE = "한반도 범위를 벗어난 좌표입니다.";

  private InvalidCoordinateException(Map<String, Object> details) {
    super(HttpStatus.BAD_REQUEST, MESSAGE, details);
  }

  public static InvalidCoordinateException of(double latitude, double longitude) {
    return new InvalidCoordinateException(Map.of("latitude", latitude, "longitude", longitude));
  }
}