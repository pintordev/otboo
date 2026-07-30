package com.sprint.mission.otboo.domain.authuser.auth.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class AuthException extends OtbooException {

  protected AuthException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}
