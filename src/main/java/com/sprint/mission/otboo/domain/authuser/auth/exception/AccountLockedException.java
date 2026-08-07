package com.sprint.mission.otboo.domain.authuser.auth.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class AccountLockedException extends AuthException{

  private static final String MESSAGE = "계정이 잠겨있습니다. 관리자에게 문의해주세요.";

  private AccountLockedException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.FORBIDDEN, MESSAGE, details, cause);
  }

  public static AccountLockedException withNone() {
    return new AccountLockedException(Map.of(), null);
  }
}
