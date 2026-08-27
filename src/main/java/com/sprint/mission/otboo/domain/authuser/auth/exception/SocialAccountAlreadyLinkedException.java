package com.sprint.mission.otboo.domain.authuser.auth.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class SocialAccountAlreadyLinkedException extends AuthException implements OAuth2FailureCode {

  private static final String MESSAGE = "이미 다른 계정에 연동된 소셜 계정입니다.";

  private SocialAccountAlreadyLinkedException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.CONFLICT, MESSAGE, details, cause);
  }

  public static SocialAccountAlreadyLinkedException withCause(Throwable cause) {
    return new SocialAccountAlreadyLinkedException(Map.of(), cause);
  }

  @Override
  public String errorCode() {
    return "social_account_already_linked";
  }
}
