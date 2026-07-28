package com.sprint.mission.otboo.external.kma.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class KmaException extends OtbooException {

  protected KmaException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}