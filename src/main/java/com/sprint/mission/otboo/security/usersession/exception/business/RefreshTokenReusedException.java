package com.sprint.mission.otboo.security.usersession.exception.business;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class RefreshTokenReusedException extends UserSessionException {

  private static final String MESSAGE = "비정상적인 동작 감지가 되었습니다. 다시 로그인해주세요.";

  private RefreshTokenReusedException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static RefreshTokenReusedException withNone() {
    return new RefreshTokenReusedException(Map.of(), null);
  }
}
