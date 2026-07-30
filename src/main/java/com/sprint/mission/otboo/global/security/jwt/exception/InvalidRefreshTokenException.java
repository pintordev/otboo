package com.sprint.mission.otboo.global.security.jwt.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class InvalidRefreshTokenException extends JwtException {

  private static final String MESSAGE = "유효하지 않은 리프레시 토큰입니다.";

  private InvalidRefreshTokenException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static InvalidRefreshTokenException withNone() {
    return new InvalidRefreshTokenException(Collections.emptyMap());
  }
}
