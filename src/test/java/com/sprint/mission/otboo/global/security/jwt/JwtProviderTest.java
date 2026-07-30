package com.sprint.mission.otboo.global.security.jwt;

import com.sprint.mission.otboo.global.security.jwt.exception.ExpiredTokenException;
import com.sprint.mission.otboo.global.security.jwt.exception.InvalidAccessTokenException;
import com.sprint.mission.otboo.global.security.jwt.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

  // 테스트 전용 더미 시크릿 (base64, 32바이트 이상). access/refresh를 서로 다르게 두어 교차 검증 테스트가 가능하게 함
  private static final String ACCESS_SECRET =
      "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS1mb3Itand0LXByb3ZpZGVyLXVuaXQtdGVzdC1wbGVhc2U=";
  private static final String REFRESH_SECRET =
      "dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktZm9yLWp3dC1wcm92aWRlci11bml0LXRlc3QtcGxlYXNl";

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, 15L, 14L);
    jwtProvider = new JwtProvider(jwtProperties);
  }

  @Nested
  @DisplayName("createAccessToken")
  class CreateAccessToken {

    @Test
    @DisplayName("subject, role, sid, iat, exp 클레임을 정확히 담은 access 토큰을 생성한다")
    void createAccessToken_containsExpectedClaims() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

      // when
      String token = jwtProvider.createAccessToken(userId, "USER", sessionId, now);
      Claims claims = jwtProvider.parseAccessTokenClaims(token);

      // then
      assertThat(claims.getSubject()).isEqualTo(userId.toString());
      assertThat(claims.get("role", String.class)).isEqualTo("USER");
      assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
      assertThat(claims.getIssuedAt().toInstant()).isEqualTo(now);
      assertThat(claims.getExpiration().toInstant()).isEqualTo(now.plus(15, ChronoUnit.MINUTES));
    }
  }

  @Nested
  @DisplayName("createRefreshToken")
  class CreateRefreshToken {

    @Test
    @DisplayName("subject, sid, jti, iat, exp 클레임을 정확히 담은 refresh 토큰을 생성한다")
    void createRefreshToken_containsExpectedClaims() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

      // when
      String token = jwtProvider.createRefreshToken(userId, sessionId, jti, now);
      Claims claims = jwtProvider.parseRefreshTokenClaims(token);

      // then
      assertThat(claims.getSubject()).isEqualTo(userId.toString());
      assertThat(claims.get("sid", String.class)).isEqualTo(sessionId.toString());
      assertThat(claims.getId()).isEqualTo(jti.toString());
      assertThat(claims.getIssuedAt().toInstant()).isEqualTo(now);
      assertThat(claims.getExpiration().toInstant()).isEqualTo(now.plus(14, ChronoUnit.DAYS));
    }
  }

  @Nested
  @DisplayName("parseAccessTokenClaims")
  class ParseAccessTokenClaims {

    @Test
    @DisplayName("만료된 access 토큰은 ExpiredTokenException을 던진다")
    void parseAccessTokenClaims_expiredToken_throwsExpiredTokenException() {
      // given: 발급 시각을 과거로 잡아 이미 만료된 토큰을 만든다
      Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
      String expiredToken = jwtProvider.createAccessToken(UUID.randomUUID(), "USER",
          UUID.randomUUID(), past);

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseAccessTokenClaims(expiredToken))
          .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("서명이 위조된 access 토큰은 InvalidAccessTokenException을 던진다")
    void parseAccessTokenClaims_tamperedToken_throwsInvalidAccessTokenException() {
      // given
      String token = jwtProvider.createAccessToken(UUID.randomUUID(), "USER", UUID.randomUUID(),
          Instant.now());
      String tamperedToken = token.substring(0, token.length() - 2) + "xx";

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseAccessTokenClaims(tamperedToken))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("refresh 토큰을 access 토큰으로 검증하려 하면 InvalidAccessTokenException을 던진다 (시크릿 분리 검증)")
    void parseAccessTokenClaims_refreshTokenGivenInstead_throwsInvalidAccessTokenException() {
      // given
      String refreshToken = jwtProvider.createRefreshToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseAccessTokenClaims(refreshToken))
          .isInstanceOf(InvalidAccessTokenException.class);
    }
  }

  @Nested
  @DisplayName("parseRefreshTokenClaims")
  class ParseRefreshTokenClaims {

    @Test
    @DisplayName("만료된 refresh 토큰은 ExpiredTokenException을 던진다")
    void parseRefreshTokenClaims_expiredToken_throwsExpiredTokenException() {
      // given
      Instant past = Instant.now().minus(30, ChronoUnit.DAYS);
      String expiredToken = jwtProvider.createRefreshToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), past);

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseRefreshTokenClaims(expiredToken))
          .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("서명이 위조된 refresh 토큰은 InvalidRefreshTokenException을 던진다")
    void parseRefreshTokenClaims_tamperedToken_throwsInvalidRefreshTokenException() {
      // given
      String token = jwtProvider.createRefreshToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
      String tamperedToken = token.substring(0, token.length() - 2) + "xx";

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseRefreshTokenClaims(tamperedToken))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("access 토큰을 refresh 토큰으로 검증하려 하면 InvalidRefreshTokenException을 던진다 (시크릿 분리 검증)")
    void parseRefreshTokenClaims_accessTokenGivenInstead_throwsInvalidRefreshTokenException() {
      // given
      String accessToken = jwtProvider.createAccessToken(
          UUID.randomUUID(), "USER", UUID.randomUUID(), Instant.now());

      // when & then
      assertThatThrownBy(() -> jwtProvider.parseRefreshTokenClaims(accessToken))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }
  }

  @Nested
  @DisplayName("만료 시각 계산")
  class ExpirationCalculation {

    @Test
    @DisplayName("getAccessTokenExpiresAt은 accessTokenExpirationMinutes만큼 뒤 시각을 반환한다")
    void getAccessTokenExpiresAt_addsConfiguredMinutes() {
      Instant now = Instant.now();
      assertThat(jwtProvider.getAccessTokenExpiresAt(now)).isEqualTo(
          now.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("getRefreshTokenExpiresAt은 refreshTokenExpirationDays만큼 뒤 시각을 반환한다")
    void getRefreshTokenExpiresAt_addsConfiguredDays() {
      Instant now = Instant.now();
      assertThat(jwtProvider.getRefreshTokenExpiresAt(now)).isEqualTo(
          now.plus(14, ChronoUnit.DAYS));
    }
  }
}
