package com.sprint.mission.otboo.security.usersession.exception.business;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class UserSessionExpiredException extends UserSessionException {

  private static final String MESSAGE = "세션이 만료되었습니다. 다시 로그인해주세요.";

  private UserSessionExpiredException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static UserSessionExpiredException withNone() {
    return new UserSessionExpiredException(Map.of(), null);
  }
}
