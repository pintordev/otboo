package com.sprint.mission.otboo.global.security.jwt.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class InvalidAccessTokenException extends JwtException {

  private static final String MESSAGE = "유효하지 않은 엑세스 토큰입니다.";

  private InvalidAccessTokenException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static InvalidAccessTokenException withNone() {
    return new InvalidAccessTokenException(Collections.emptyMap());
  }
}
