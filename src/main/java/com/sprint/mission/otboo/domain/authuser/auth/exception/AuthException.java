package com.sprint.mission.otboo.domain.authuser.auth.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class AuthException extends OtbooException {

  protected AuthException(HttpStatus status, String message,
      Map<String, Object> details, Throwable cause) {
    super(status, message, details, cause);
  }
}
