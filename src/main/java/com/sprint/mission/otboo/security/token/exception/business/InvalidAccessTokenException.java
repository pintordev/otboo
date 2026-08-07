package com.sprint.mission.otboo.security.token.exception.business;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidAccessTokenException extends TokenException {

  private static final String MESSAGE = "유효하지 않은 액세스 토큰입니다.";

  private InvalidAccessTokenException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static InvalidAccessTokenException withNone() {
    return new InvalidAccessTokenException(Map.of(), null);
  }

  public static InvalidAccessTokenException withCause(Throwable cause) {
    return new InvalidAccessTokenException(Map.of(), cause);
  }
}
