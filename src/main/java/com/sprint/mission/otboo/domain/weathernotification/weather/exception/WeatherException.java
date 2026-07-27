package com.sprint.mission.otboo.domain.weathernotification.weather.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class WeatherException extends OtbooException {

  protected WeatherException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}