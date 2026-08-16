package com.sprint.mission.otboo.security.token.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.exception.system.TokenProviderException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.properties.enums.TokenImplType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// jjwt/nimbus 구현체가 지켜야 할 공통 계약을 한 번만 정의하고, 두 구현체의 테스트가 상속받아 재사용한다.
public abstract class AbstractTokenProviderContractTest {

  protected static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(15);
  protected static final Duration REFRESH_EXPIRATION = Duration.ofDays(14);
  protected static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  protected static final Instant ACCESS_TOKEN_EXP = NOW.plus(ACCESS_EXPIRATION);
  protected static final Instant REFRESH_TOKEN_EXP = NOW.plus(REFRESH_EXPIRATION);

  protected static final String ACCESS_SECRET = encode(32, (byte) 1);
  protected static final String REFRESH_SECRET = encode(32, (byte) 2);
  protected static final String OTHER_SECRET = encode(32, (byte) 9);
  protected static final String WEAK_SECRET = encode(10, (byte) 1);

  protected abstract TokenProvider createProvider(TokenProperties properties, Clock clock);

  protected abstract String craftToken(String base64Secret, String subject, String sid, String role,
      String jti, Instant issuedAt, Instant expiration);

  private static String encode(int length, byte fill) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, fill);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private TokenProperties validProperties() {
    return new TokenProperties(ACCESS_EXPIRATION, REFRESH_EXPIRATION, ACCESS_SECRET, REFRESH_SECRET,
        TokenImplType.NIMBUS);
  }

  private TokenProvider providerAt(Instant now) {
    return createProvider(validProperties(), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Nested
  class Constructor {

    @Test
    void 성공_유효한_시크릿이면_정상적으로_생성된다() {
      // given
      TokenProperties properties = validProperties();

      // when & then
      assertThatCode(() -> createProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
          .doesNotThrowAnyException();
    }

    @Test
    void 실패_accessSecret이_base64형식이_아니면_TokenProviderException이_발생한다() {
      // given
      TokenProperties properties = new TokenProperties(
          ACCESS_EXPIRATION, REFRESH_EXPIRATION, "not!!valid==base64", REFRESH_SECRET,
          TokenImplType.NIMBUS);

      // when & then
      assertThatThrownBy(() -> createProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
          .isInstanceOf(TokenProviderException.class);
    }

    @Test
    void 실패_refreshSecret이_base64형식이_아니면_TokenProviderException이_발생한다() {
      // given
      TokenProperties properties = new TokenProperties(
          ACCESS_EXPIRATION, REFRESH_EXPIRATION, ACCESS_SECRET, "not!!valid==base64",
          TokenImplType.NIMBUS);

      // when & then
      assertThatThrownBy(() -> createProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
          .isInstanceOf(TokenProviderException.class);
    }

    @Test
    void 실패_accessSecret길이가_HS256_최소_길이보다_짧으면_TokenProviderException이_발생한다() {
      // given
      TokenProperties properties = new TokenProperties(
          ACCESS_EXPIRATION, REFRESH_EXPIRATION, WEAK_SECRET, REFRESH_SECRET,
          TokenImplType.NIMBUS);

      // when & then
      assertThatThrownBy(() -> createProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
          .isInstanceOf(TokenProviderException.class);
    }

    @Test
    void 실패_refreshSecret길이가_HS256_최소_길이보다_짧으면_TokenProviderException이_발생한다() {
      // given
      TokenProperties properties = new TokenProperties(
          ACCESS_EXPIRATION, REFRESH_EXPIRATION, ACCESS_SECRET, WEAK_SECRET,
          TokenImplType.NIMBUS);

      // when & then
      assertThatThrownBy(() -> createProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
          .isInstanceOf(TokenProviderException.class);
    }
  }

  @Nested
  class CreateAccessToken {

    @Test
    void 성공_점으로_구분된_3개의_세그먼트를_가진_토큰을_반환한다() {
      // given
      TokenProvider provider = providerAt(NOW);

      // when
      String token = provider.createAccessToken(UUID.randomUUID(), UUID.randomUUID(), "ROLE_USER", NOW);

      // then
      assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void 성공_파싱하면_생성시_전달한_클레임이_그대로_복원된다() {
      // given
      TokenProvider provider = providerAt(NOW);
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      String role = "ROLE_ADMIN";

      // when
      String token = provider.createAccessToken(userId, sessionId, role, NOW);
      AccessTokenClaims claims = provider.parseAccessToken(token);

      // then
      assertThat(claims).isEqualTo(new AccessTokenClaims(userId, sessionId, role));
    }
  }

  @Nested
  class CreateRefreshToken {

    @Test
    void 성공_점으로_구분된_3개의_세그먼트를_가진_토큰을_반환한다() {
      // given
      TokenProvider provider = providerAt(NOW);

      // when
      String token = provider.createRefreshToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);

      // then
      assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void 성공_파싱하면_생성시_전달한_클레임이_그대로_복원된다() {
      // given
      TokenProvider provider = providerAt(NOW);
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();

      // when
      String token = provider.createRefreshToken(userId, sessionId, jti, NOW);
      RefreshTokenClaims claims = provider.parseRefreshToken(token);

      // then
      assertThat(claims).isEqualTo(new RefreshTokenClaims(userId, sessionId, jti));
    }
  }

  @Nested
  class ParseAccessToken {

    @Test
    void 성공_유효한_토큰이면_클레임을_반환한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      String token = craftToken(ACCESS_SECRET, userId.toString(), sessionId.toString(), "ROLE_USER", null,
          NOW, ACCESS_TOKEN_EXP);

      // when
      AccessTokenClaims claims = provider.parseAccessToken(token);

      // then
      assertThat(claims).isEqualTo(new AccessTokenClaims(userId, sessionId, "ROLE_USER"));
    }

    @Test
    void 성공_만료_1초_전이면_정상적으로_파싱된다() {
      // given
      TokenProvider provider = providerAt(ACCESS_TOKEN_EXP.minusSeconds(1));
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          "ROLE_USER", null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatCode(() -> provider.parseAccessToken(token)).doesNotThrowAnyException();
    }

    @Test
    void 실패_만료된_토큰이면_ExpiredTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(ACCESS_TOKEN_EXP.plusSeconds(1));
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          "ROLE_USER", null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token)).isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    void 실패_다른_시크릿으로_서명된_토큰이면_InvalidAccessTokenException이_발생하고_원인이_없다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(OTHER_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          "ROLE_USER", null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class)
          .hasNoCause();
    }

    @Test
    void 실패_refreshToken_시크릿으로_서명된_토큰이면_InvalidAccessTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          "ROLE_USER", null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 실패_구조가_깨진_문자열이면_InvalidAccessTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt-token"))
          .isInstanceOf(InvalidAccessTokenException.class)
          .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void 실패_sid클레임이_없으면_InvalidAccessTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), null, "ROLE_USER", null,
          NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 실패_role클레임이_없으면_InvalidAccessTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 실패_role이_공백이면_InvalidAccessTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          "   ", null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 실패_subject가_UUID형식이_아니면_InvalidAccessTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, "not-a-uuid", UUID.randomUUID().toString(), "ROLE_USER",
          null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실패_sid가_UUID형식이_아니면_InvalidAccessTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), "not-a-uuid", "ROLE_USER",
          null, NOW, ACCESS_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ParseRefreshToken {

    @Test
    void 성공_유효한_토큰이면_클레임을_반환한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      String token = craftToken(REFRESH_SECRET, userId.toString(), sessionId.toString(), null,
          jti.toString(), NOW, REFRESH_TOKEN_EXP);

      // when
      RefreshTokenClaims claims = provider.parseRefreshToken(token);

      // then
      assertThat(claims).isEqualTo(new RefreshTokenClaims(userId, sessionId, jti));
    }

    @Test
    void 성공_만료_1초_전이면_정상적으로_파싱된다() {
      // given
      TokenProvider provider = providerAt(REFRESH_TOKEN_EXP.minusSeconds(1));
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatCode(() -> provider.parseRefreshToken(token)).doesNotThrowAnyException();
    }

    @Test
    void 실패_만료된_토큰이면_ExpiredTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(REFRESH_TOKEN_EXP.plusSeconds(1));
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token)).isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    void 실패_다른_시크릿으로_서명된_토큰이면_InvalidRefreshTokenException이_발생하고_원인이_없다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(OTHER_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class)
          .hasNoCause();
    }

    @Test
    void 실패_accessToken_시크릿으로_서명된_토큰이면_InvalidRefreshTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(ACCESS_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 실패_구조가_깨진_문자열이면_InvalidRefreshTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken("not-a-jwt-token"))
          .isInstanceOf(InvalidRefreshTokenException.class)
          .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void 실패_sid클레임이_없으면_InvalidRefreshTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), null, null,
          UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 실패_jti클레임이_없으면_InvalidRefreshTokenException이_발생한다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, null, NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 실패_subject가_UUID형식이_아니면_InvalidRefreshTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, "not-a-uuid", UUID.randomUUID().toString(), null,
          UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실패_sid가_UUID형식이_아니면_InvalidRefreshTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), "not-a-uuid", null,
          UUID.randomUUID().toString(), NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실패_jti가_UUID형식이_아니면_InvalidRefreshTokenException이_발생하고_원인이_있다() {
      // given
      TokenProvider provider = providerAt(NOW);
      String token = craftToken(REFRESH_SECRET, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
          null, "not-a-uuid", NOW, REFRESH_TOKEN_EXP);

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }
}
