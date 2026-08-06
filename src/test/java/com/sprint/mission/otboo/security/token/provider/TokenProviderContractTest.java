package com.sprint.mission.otboo.security.token.provider;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class TokenProviderContractTest {

  private static final String ACCESS_SECRET =
      "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS1mb3Itand0LXByb3ZpZGVyLXVuaXQtdGVzdC1wbGVhc2U=";
  private static final String REFRESH_SECRET =
      "dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktZm9yLWp3dC1wcm92aWRlci11bml0LXRlc3QtcGxlYXNl";

  protected abstract TokenProvider tokenProvider(TokenProperties properties, Clock clock);

  private TokenProperties defaultProperties() {
    return new TokenProperties(15, 14, ACCESS_SECRET, REFRESH_SECRET);
  }

  @Nested
  @DisplayName("액세스 토큰")
  class AccessToken {

    @Test
    @DisplayName("발급한 토큰을 파싱하면 원래 값 그대로 복원된다")
    void createAndParseAccessToken_roundTrip() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();

      String token = provider.createAccessToken(userId, "USER", sessionId, Instant.now());
      AccessTokenClaims claims = provider.parseAccessToken(token);

      assertThat(claims.userId()).isEqualTo(userId);
      assertThat(claims.role()).isEqualTo("USER");
      assertThat(claims.sessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하면 ExpiredTokenException을 던진다")
    void parseAccessToken_expired_throwsExpiredTokenException() {
      Clock issuedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
      TokenProvider issuingProvider = tokenProvider(defaultProperties(), issuedClock);
      String token = issuingProvider.createAccessToken(
          UUID.randomUUID(), "USER", UUID.randomUUID(), Instant.now(issuedClock));

      Clock laterClock = Clock.fixed(
          Instant.parse("2026-01-01T00:00:00Z").plus(20, ChronoUnit.MINUTES), ZoneOffset.UTC);
      TokenProvider verifyingProvider = tokenProvider(defaultProperties(), laterClock);

      assertThatThrownBy(() -> verifyingProvider.parseAccessToken(token))
          .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("다른 키로 서명 검증하면 InvalidAccessTokenException을 던진다")
    void parseAccessToken_wrongSignature_throwsInvalidAccessTokenException() {
      TokenProvider issuingProvider = tokenProvider(defaultProperties(), Clock.systemUTC());
      String token = issuingProvider.createAccessToken(
          UUID.randomUUID(), "USER", UUID.randomUUID(), Instant.now());

      TokenProperties otherProperties = new TokenProperties(15, 14,
          "b3RoZXItYWNjZXNzLXNlY3JldC1rZXktZm9yLXRlc3QtcHVycG9zZS1vbmx5LTMy", REFRESH_SECRET);
      TokenProvider verifyingProvider = tokenProvider(otherProperties, Clock.systemUTC());

      assertThatThrownBy(() -> verifyingProvider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("깨진 토큰 문자열을 파싱하면 InvalidAccessTokenException을 던진다")
    void parseAccessToken_malformed_throwsInvalidAccessTokenException() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());

      assertThatThrownBy(() -> provider.parseAccessToken("this-is-not-a-jwt"))
          .isInstanceOf(InvalidAccessTokenException.class);
    }
  }

  @Nested
  @DisplayName("리프레시 토큰")
  class RefreshToken {

    @Test
    @DisplayName("발급한 토큰을 파싱하면 원래 값 그대로 복원된다")
    void createAndParseRefreshToken_roundTrip() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      Instant now = Instant.now();

      String token = provider.createRefreshToken(userId, jti, sessionId, now,
          now.plus(14, ChronoUnit.DAYS));
      RefreshTokenClaims claims = provider.parseRefreshToken(token);

      assertThat(claims.userId()).isEqualTo(userId);
      assertThat(claims.sessionId()).isEqualTo(sessionId);
      assertThat(claims.jti()).isEqualTo(jti);
    }

    @Test
    @DisplayName("만료 시각이 지난 토큰을 파싱하면 ExpiredTokenException을 던진다")
    void parseRefreshToken_expired_throwsExpiredTokenException() {
      Clock issuedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
      TokenProvider issuingProvider = tokenProvider(defaultProperties(), issuedClock);
      Instant now = Instant.now(issuedClock);
      String token = issuingProvider.createRefreshToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now,
          now.plus(1, ChronoUnit.MINUTES));

      Clock laterClock = Clock.fixed(Instant.parse("2026-01-01T00:05:00Z"), ZoneOffset.UTC);
      TokenProvider verifyingProvider = tokenProvider(defaultProperties(), laterClock);

      assertThatThrownBy(() -> verifyingProvider.parseRefreshToken(token))
          .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("다른 키로 서명 검증하면 InvalidRefreshTokenException을 던진다")
    void parseRefreshToken_wrongSignature_throwsInvalidRefreshTokenException() {
      TokenProvider issuingProvider = tokenProvider(defaultProperties(), Clock.systemUTC());
      Instant now = Instant.now();
      String token = issuingProvider.createRefreshToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now,
          now.plus(14, ChronoUnit.DAYS));

      TokenProperties otherProperties = new TokenProperties(15, 14, ACCESS_SECRET,
          "YW5vdGhlci1yZWZyZXNoLXNlY3JldC1rZXktZm9yLXRlc3QtcHVycG9zZS0zMg==");
      TokenProvider verifyingProvider = tokenProvider(otherProperties, Clock.systemUTC());

      assertThatThrownBy(() -> verifyingProvider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }
  }

  @Nested
  @DisplayName("만료 시각/TTL 계산")
  class ExpirationCalculation {

    @Test
    @DisplayName("getAccessTokenExpiresAt은 설정된 분(minute)만큼 더한 시각을 반환한다")
    void getAccessTokenExpiresAt_addsConfiguredMinutes() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());
      Instant now = Instant.now();

      assertThat(provider.getAccessTokenExpiresAt(now)).isEqualTo(now.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("getRefreshTokenExpiresAt은 설정된 일(day)만큼 더한 시각을 반환한다")
    void getRefreshTokenExpiresAt_addsConfiguredDays() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());
      Instant now = Instant.now();

      assertThat(provider.getRefreshTokenExpiresAt(now)).isEqualTo(now.plus(14, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("getRefreshTokenTtl은 설정된 일(day) 수의 Duration을 반환한다")
    void getRefreshTokenTtl_returnsDurationOfConfiguredDays() {
      TokenProvider provider = tokenProvider(defaultProperties(), Clock.systemUTC());

      assertThat(provider.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(14));
    }
  }
}
