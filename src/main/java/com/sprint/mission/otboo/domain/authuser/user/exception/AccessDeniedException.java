package com.sprint.mission.otboo.domain.authuser.user.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class AccessDeniedException extends UserException{

  private static final String MESSAGE = "권한이 없습니다.";

  private AccessDeniedException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.FORBIDDEN, MESSAGE, details, cause);
  }

  public static AccessDeniedException withNone() {
    return new AccessDeniedException(Map.of(), null);
  }
}
