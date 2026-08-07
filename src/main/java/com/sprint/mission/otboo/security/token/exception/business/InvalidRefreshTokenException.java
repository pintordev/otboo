package com.sprint.mission.otboo.security.token.exception.business;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends TokenException {

  private static final String MESSAGE = "유효하지 않은 리프레시 토큰입니다.";

  private InvalidRefreshTokenException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static InvalidRefreshTokenException withNone() {
    return new InvalidRefreshTokenException(Map.of(), null);
  }

  public static InvalidRefreshTokenException withCause(Throwable cause) {
    return new InvalidRefreshTokenException(Map.of(), cause);
  }
}
