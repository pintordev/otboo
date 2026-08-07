package com.sprint.mission.otboo.security.token.exception.system;

public class TokenProviderException extends RuntimeException{

  private TokenProviderException(String message, Throwable cause) {
    super(message, cause);
  }

  public static TokenProviderException withMessageAndCause(String message, Throwable cause) {
    return new TokenProviderException(message, cause);
  }
}
