package com.sprint.mission.otboo.security.token.exception.business;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ExpiredTokenException extends TokenException {

  private static final String MESSAGE = "만료된 토큰입니다.";

  private ExpiredTokenException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static ExpiredTokenException withNone() {
    return new ExpiredTokenException(Map.of(), null);
  }

  public static ExpiredTokenException withCause(Throwable cause) {
    return new ExpiredTokenException(Map.of(), cause);
  }
}
