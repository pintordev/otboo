package com.sprint.mission.otboo.domain.authuser.user.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends UserException {

  private static final String MESSAGE = "이미 사용 중인 이메일입니다.";

  private DuplicateEmailException(Map<String, Object> details) {
    super(HttpStatus.CONFLICT, MESSAGE, details);
  }

  public static DuplicateEmailException withEmail(String email) {
    return new DuplicateEmailException(Map.of("email", email));
  }
}