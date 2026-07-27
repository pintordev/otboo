package com.sprint.mission.otboo.global.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class OtbooException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, Object> details;

  protected OtbooException(HttpStatus status, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}