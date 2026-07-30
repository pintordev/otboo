package com.sprint.mission.otboo.global.usersession.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class RefreshTokenReusedException extends UserSessionException {

  private static final String MESSAGE = "토큰 탈취 위협이 감지되었습니다. 재로그인해주세요.";

  private RefreshTokenReusedException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static RefreshTokenReusedException withNone() {
    return new RefreshTokenReusedException(Collections.emptyMap());
  }
}
