package com.sprint.mission.otboo.global.security.jwt.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class ExpiredTokenException extends JwtException {

  private static final String MESSAGE = "만료된 토큰입니다.";

  private ExpiredTokenException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static ExpiredTokenException withNone() {
    return new ExpiredTokenException(Collections.emptyMap());
  }
}
