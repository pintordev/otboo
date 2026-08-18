package com.sprint.mission.otboo.security.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.properties.enums.TokenImplType;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import com.sprint.mission.otboo.security.usersession.properties.enums.ConcurrentPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.ExpirationPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.UserSessionRegistryType;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SecurityExpirationConsistencyValidator")
class SecurityExpirationConsistencyValidatorTest {

  private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

  private static RefreshTokenCookieProperties cookieProperties(Duration refreshTokenExpiration) {
    return new RefreshTokenCookieProperties(refreshTokenExpiration, false);
  }

  private static UserSessionProperties userSessionProperties(Duration userSessionExpiration) {
    return new UserSessionProperties(userSessionExpiration, 3, ExpirationPolicyType.ABSOLUTE,
        ConcurrentPolicyType.MULTI, UserSessionRegistryType.REDIS);
  }

  private static TokenProperties tokenProperties(Duration accessTokenExpiration, Duration refreshTokenExpiration) {
    return new TokenProperties(accessTokenExpiration, refreshTokenExpiration, SECRET, SECRET,
        TokenImplType.NIMBUS);
  }

  private static SecurityExpirationConsistencyValidator validator(
      RefreshTokenCookieProperties cookie, UserSessionProperties userSession, TokenProperties token) {
    return new SecurityExpirationConsistencyValidator(cookie, userSession, token);
  }

  @Nested
  @DisplayName("리프레시 쿠키/토큰 만료시간 정합성 검증")
  class RefreshCookieVsRefreshToken {

    @Test
    @DisplayName("실패_쿠키_만료시간이_토큰_만료시간보다_짧으면_예외가_발생한다")
    void 실패_쿠키_만료시간이_토큰_만료시간보다_짧으면_예외가_발생한다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(1));
      UserSessionProperties userSession = userSessionProperties(Duration.ofDays(14));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("성공_쿠키_만료시간이_토큰_만료시간과_같으면_예외가_발생하지_않는다")
    void 성공_쿠키_만료시간이_토큰_만료시간과_같으면_예외가_발생하지_않는다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(14));
      UserSessionProperties userSession = userSessionProperties(Duration.ofDays(14));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공_쿠키_만료시간이_토큰_만료시간보다_길면_예외가_발생하지_않는다")
    void 성공_쿠키_만료시간이_토큰_만료시간보다_길면_예외가_발생하지_않는다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(30));
      UserSessionProperties userSession = userSessionProperties(Duration.ofDays(14));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("세션/액세스 토큰 만료시간 정합성 검증")
  class UserSessionVsAccessToken {

    @Test
    @DisplayName("실패_세션_만료시간이_액세스_토큰_만료시간보다_짧으면_예외가_발생한다")
    void 실패_세션_만료시간이_액세스_토큰_만료시간보다_짧으면_예외가_발생한다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(14));
      UserSessionProperties userSession = userSessionProperties(Duration.ofMinutes(10));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("성공_세션_만료시간이_액세스_토큰_만료시간과_같으면_예외가_발생하지_않는다")
    void 성공_세션_만료시간이_액세스_토큰_만료시간과_같으면_예외가_발생하지_않는다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(14));
      UserSessionProperties userSession = userSessionProperties(Duration.ofMinutes(30));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공_세션_만료시간이_액세스_토큰_만료시간보다_길면_예외가_발생하지_않는다")
    void 성공_세션_만료시간이_액세스_토큰_만료시간보다_길면_예외가_발생하지_않는다() {
      // given
      RefreshTokenCookieProperties cookie = cookieProperties(Duration.ofDays(14));
      UserSessionProperties userSession = userSessionProperties(Duration.ofDays(14));
      TokenProperties token = tokenProperties(Duration.ofMinutes(30), Duration.ofDays(14));
      SecurityExpirationConsistencyValidator validator = validator(cookie, userSession, token);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }
}
