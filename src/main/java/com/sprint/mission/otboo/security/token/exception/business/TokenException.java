package com.sprint.mission.otboo.security.token.exception.business;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class TokenException extends OtbooException {

  protected TokenException(HttpStatus status, String message,
      Map<String, Object> details, Throwable cause) {
    super(status, message, details, cause);
  }
}
