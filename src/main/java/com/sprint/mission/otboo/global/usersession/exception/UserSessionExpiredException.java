package com.sprint.mission.otboo.global.usersession.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class UserSessionExpiredException extends UserSessionException {

  private final static String MESSAGE = "재로그인 해주세요.";

  private UserSessionExpiredException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static UserSessionExpiredException withNone() {
    return new UserSessionExpiredException(Collections.emptyMap());
  }
}
