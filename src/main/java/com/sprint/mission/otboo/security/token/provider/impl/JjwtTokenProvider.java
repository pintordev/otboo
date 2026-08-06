package com.sprint.mission.otboo.security.token.provider.impl;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.exception.TokenException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    this.accessSecretKey = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(tokenProperties.accessSecret()));
    this.refreshSecretKey = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(tokenProperties.refreshSecret()));
  }

  @Override
  public String createAccessToken(UUID userId, String role, UUID sessionId, Instant now) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("role", role)
        .claim("sid", sessionId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(getAccessTokenExpiresAt(now)))
        .signWith(accessSecretKey)
        .compact();
  }

  @Override
  public String createRefreshToken(UUID userId, UUID jti, UUID sessionId, Instant now,
      Instant expiresAt) {
    return Jwts.builder()
        .subject(userId.toString())
        .id(jti.toString())
        .claim("sid", sessionId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(refreshSecretKey)
        .compact();
  }

  @Override
  public AccessTokenClaims parseAccessToken(String token) {
    Claims claims = verifyAndParse(token, accessSecretKey,
        InvalidAccessTokenException::withNone, InvalidAccessTokenException::withCause);
    try {
      String role = claims.get("role", String.class);
      if (role == null || role.isBlank()) {
        throw InvalidAccessTokenException.withNone();
      }
      return new AccessTokenClaims(
          UUID.fromString(claims.getSubject()),
          role,
          UUID.fromString(claims.get("sid", String.class))
      );
    } catch (IllegalArgumentException | NullPointerException e) {
      throw InvalidAccessTokenException.withCause(e);
    }
  }

  @Override
  public RefreshTokenClaims parseRefreshToken(String token) {
    Claims claims = verifyAndParse(token, refreshSecretKey,
        InvalidRefreshTokenException::withNone, InvalidRefreshTokenException::withCause);
    try {
      return new RefreshTokenClaims(
          UUID.fromString(claims.getSubject()),
          UUID.fromString(claims.get("sid", String.class)),
          UUID.fromString(claims.getId())
      );
    } catch (IllegalArgumentException | NullPointerException e) {
      throw InvalidRefreshTokenException.withCause(e);
    }
  }

  @Override
  public Instant getAccessTokenExpiresAt(Instant from) {
    return from.plus(tokenProperties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);
  }

  @Override
  public Instant getRefreshTokenExpiresAt(Instant from) {
    return from.plus(tokenProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
  }

  @Override
  public Duration getRefreshTokenTtl() {
    return Duration.ofDays(tokenProperties.refreshTokenExpirationDays());
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
      throw ExpiredTokenException.withNone(); // 정상 서명 + 만료: refresh 유도 대상
    } catch (JwtException | IllegalArgumentException e) {
      throw invalidTokenFactory.apply(e); // 구조 깨짐/검증 오류: cause 보존
    }
  }
}
