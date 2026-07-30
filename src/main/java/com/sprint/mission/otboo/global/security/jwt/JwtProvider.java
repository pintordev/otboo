package com.sprint.mission.otboo.global.security.jwt;

import com.sprint.mission.otboo.global.security.jwt.exception.ExpiredTokenException;
import com.sprint.mission.otboo.global.security.jwt.exception.InvalidAccessTokenException;
import com.sprint.mission.otboo.global.security.jwt.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

  private final JwtProperties jwtProperties;
  private final SecretKey accessSecretKey;
  private final SecretKey refreshSecretKey;

  public JwtProvider(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.accessSecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.accessSecret()));
    this.refreshSecretKey = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(jwtProperties.refreshSecret()));
  }

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

  public String createRefreshToken(UUID userId, UUID sessionId, UUID jti, Instant now) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("sid", sessionId.toString())
        .id(jti.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(getRefreshTokenExpiresAt(now)))
        .signWith(refreshSecretKey)
        .compact();
  }

  public Claims parseAccessTokenClaims(String token) {
    try {
      return Jwts.parser().verifyWith(accessSecretKey).build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw ExpiredTokenException.withNone();
    } catch (JwtException | IllegalArgumentException e) {
      throw InvalidAccessTokenException.withNone();
    }
  }

  public Claims parseRefreshTokenClaims(String token) {
    try {
      return Jwts.parser().verifyWith(refreshSecretKey).build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw ExpiredTokenException.withNone();
    } catch (JwtException | IllegalArgumentException e) {
      throw InvalidRefreshTokenException.withNone();
    }
  }

  public Instant getAccessTokenExpiresAt(Instant now) {
    return now.plus(jwtProperties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);
  }

  public Instant getRefreshTokenExpiresAt(Instant now) {
    return now.plus(jwtProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
  }
}
