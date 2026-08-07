package com.sprint.mission.otboo.security.token.provider.impl;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.exception.business.TokenException;
import com.sprint.mission.otboo.security.token.exception.system.TokenProviderException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.crypto.SecretKey;

public class JjwtTokenProvider implements TokenProvider {

  private final TokenProperties tokenProperties;
  private final Clock clock;
  private final SecretKey accessSecretKey;
  private final SecretKey refreshSecretKey;

  public JjwtTokenProvider(TokenProperties tokenProperties, Clock clock) {
    this.tokenProperties = tokenProperties;
    this.clock = clock;
    try {
      byte[] accessSecret = Base64.getDecoder().decode(tokenProperties.accessSecret());
      byte[] refreshSecret = Base64.getDecoder().decode(tokenProperties.refreshSecret());
      this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret);
      this.refreshSecretKey = Keys.hmacShaKeyFor(refreshSecret);
    } catch (WeakKeyException e) {
      throw TokenProviderException.withMessageAndCause("토큰 키 초기화 실패", e);
    } catch (IllegalArgumentException e) {
      // Base64.getDecoder().decode()가 잘못된 base64 형식일 때 던짐
      throw TokenProviderException.withMessageAndCause("토큰 시크릿 base64 디코딩 실패", e);
    }
  }

  @Override
  public String createAccessToken(UUID userId, UUID sessionId, String role, Instant now) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("sid", sessionId.toString())
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(tokenProperties.accessTokenExpiration())))
        .signWith(accessSecretKey)
        .compact();
  }

  @Override
  public String createRefreshToken(UUID userId, UUID sessionId, UUID jti, Instant now) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("sid", sessionId.toString())
        .id(jti.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(tokenProperties.refreshTokenExpiration())))
        .signWith(refreshSecretKey)
        .compact();
  }

  @Override
  public AccessTokenClaims parseAccessToken(String token) {
    Claims claims = verifyAndParse(token, accessSecretKey,
        InvalidAccessTokenException::withNone, InvalidAccessTokenException::withCause);
    try {
      String subject = claims.getSubject();
      String sid = claims.get("sid", String.class);
      String role = claims.get("role", String.class);

      if (subject == null || sid == null || role == null || role.isBlank()) {
        throw InvalidAccessTokenException.withNone();
      }

      return new AccessTokenClaims(
          UUID.fromString(subject),
          UUID.fromString(sid),
          role
      );
    } catch (IllegalArgumentException e) {
      throw InvalidAccessTokenException.withCause(e);
    }
  }

  @Override
  public RefreshTokenClaims parseRefreshToken(String token) {
    Claims claims = verifyAndParse(token, refreshSecretKey,
        InvalidRefreshTokenException::withNone, InvalidRefreshTokenException::withCause);
    try {
      String subject = claims.getSubject();
      String sid = claims.get("sid", String.class);
      String jti = claims.getId();

      if (subject == null || sid == null || jti == null) {
        throw InvalidRefreshTokenException.withNone();
      }

      return new RefreshTokenClaims(
          UUID.fromString(subject),
          UUID.fromString(sid),
          UUID.fromString(jti)
      );
    } catch (IllegalArgumentException e) {
      throw InvalidRefreshTokenException.withCause(e);
    }
  }

  private Claims verifyAndParse(String token, SecretKey key,
      Supplier<? extends TokenException> invalidTokenSupplier,
      Function<Throwable, ? extends TokenException> invalidTokenFactory) {
    try {
      return Jwts.parser()
          .clock(() -> Date.from(Instant.now(clock)))
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw ExpiredTokenException.withNone(); // 정상 서명 + 만료
    } catch (SignatureException e) {
      throw invalidTokenSupplier.get();       // 서명 불일치: Nimbus처럼 원인 예외 없이 던짐
    } catch (JwtException | IllegalArgumentException e) {
      throw invalidTokenFactory.apply(e);     // 기타 구조 깨짐/형식 오류: cause 보존
    }
  }
}
