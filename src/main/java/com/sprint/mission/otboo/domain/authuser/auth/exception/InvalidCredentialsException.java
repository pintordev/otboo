package com.sprint.mission.otboo.domain.authuser.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class InvalidCredentialsException extends AuthException {

  private static final String MESSAGE = "아이디 혹은 비밀번호가 잘못되었습니다.";

  private InvalidCredentialsException(Map<String, Object> details) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details);
  }

  public static InvalidCredentialsException withNone() {
    return new InvalidCredentialsException(Collections.emptyMap());
  }
}
