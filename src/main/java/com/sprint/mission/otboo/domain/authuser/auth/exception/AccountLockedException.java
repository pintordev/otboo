package com.sprint.mission.otboo.domain.authuser.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AccountLockedException extends AuthException {

  private static final String MESSAGE = "계정이 잠겨있습니다. 관리자에게 문의바랍니다.";

  private AccountLockedException(Map<String, Object> details) {
    super(HttpStatus.FORBIDDEN, MESSAGE, details);
  }

  public static AccountLockedException withEmail(String email) {
    return new AccountLockedException(Map.of("email", email));
  }
}
