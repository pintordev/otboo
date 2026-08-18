package com.sprint.mission.otboo.security.validator;

import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityExpirationConsistencyValidator {

  private final RefreshTokenCookieProperties cookieProperties;
  private final UserSessionProperties userSessionProperties;
  private final TokenProperties tokenProperties;

  @PostConstruct
  public void validate() {
    validateRefreshCookieNotShorterThanRefreshToken();
    validateUserSessionNotShorterThanAccessToken();
  }

  private void validateRefreshCookieNotShorterThanRefreshToken() {
    if (cookieProperties.refreshTokenExpiration().compareTo(tokenProperties.refreshTokenExpiration()) < 0) {
      throw new IllegalStateException(
          "refresh-cookie.refresh-token-expiration은 token.refresh-token-expiration보다 짧을 수 없습니다.");
    }
  }

  private void validateUserSessionNotShorterThanAccessToken() {
    if (userSessionProperties.userSessionExpiration().compareTo(tokenProperties.accessTokenExpiration()) < 0) {
      throw new IllegalStateException(
          "user-session.user-session-expiration은 token.access-token-expiration보다 짧을 수 없습니다.");
    }
  }
}
