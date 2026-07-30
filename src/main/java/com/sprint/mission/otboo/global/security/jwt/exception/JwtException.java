package com.sprint.mission.otboo.global.security.jwt.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class JwtException extends OtbooException {

  protected JwtException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}
