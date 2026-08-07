package com.sprint.mission.otboo.domain.authuser.user.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends UserException{

  private static final String MESSAGE = "사용자를 찾을 수 없습니다.";

  private UserNotFoundException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.NOT_FOUND, MESSAGE, details, cause);
  }

  public static UserNotFoundException withNone() {
    return new UserNotFoundException(Map.of(), null);
  }
}
