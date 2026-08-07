package com.sprint.mission.otboo.domain.authuser.auth.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AuthException{

  private static final String MESSAGE = "아이디 혹은 비밀번호가 잘못되었습니다.";

  private InvalidCredentialsException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.UNAUTHORIZED, MESSAGE, details, cause);
  }

  public static InvalidCredentialsException withNone() {
    return new InvalidCredentialsException(Map.of(), null);
  }
}
