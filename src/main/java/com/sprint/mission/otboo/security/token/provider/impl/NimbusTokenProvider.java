package com.sprint.mission.otboo.security.token.provider.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.exception.TokenException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class NimbusTokenProvider implements TokenProvider {

  private static final JWSHeader JWS_HEADER = new JWSHeader.Builder(JWSAlgorithm.HS256)
      .type(JOSEObjectType.JWT)
      .build();

  private final TokenProperties tokenProperties;
  private final Clock clock;
  private final JWSSigner accessSigner;
  private final JWSVerifier accessVerifier;
  private final JWSSigner refreshSigner;
  private final JWSVerifier refreshVerifier;

  public NimbusTokenProvider(TokenProperties tokenProperties, Clock clock) {
    this.tokenProperties = tokenProperties;
    this.clock = clock;
    try {
      byte[] accessSecret = Base64.getDecoder().decode(tokenProperties.accessSecret());
      byte[] refreshSecret = Base64.getDecoder().decode(tokenProperties.refreshSecret());
      this.accessSigner = new MACSigner(accessSecret);
      this.accessVerifier = new MACVerifier(accessSecret);
      this.refreshSigner = new MACSigner(refreshSecret);
      this.refreshVerifier = new MACVerifier(refreshSecret);
    } catch (JOSEException e) {
      throw new IllegalStateException("토큰 키 초기화 실패", e);
    }
  }

  @Override
  public String createAccessToken(UUID userId, String role, UUID sessionId, Instant now) {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(userId.toString())
        .claim("role", role)
        .claim("sid", sessionId.toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(getAccessTokenExpiresAt(now)))
        .build();
    return sign(claims, accessSigner);
  }

  @Override
  public String createRefreshToken(UUID userId, UUID jti, UUID sessionId, Instant now,
      Instant expiresAt) {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(userId.toString())
        .jwtID(jti.toString())
        .claim("sid", sessionId.toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(expiresAt))
        .build();
    return sign(claims, refreshSigner);
  }

  @Override
  public AccessTokenClaims parseAccessToken(String token) {
    JWTClaimsSet claims = verifyAndParse(token, Instant.now(clock), accessVerifier,
        InvalidAccessTokenException::withNone, InvalidAccessTokenException::withCause);
    try {
      String role = claims.getStringClaim("role");
      if (role == null || role.isBlank()) {
        throw InvalidAccessTokenException.withNone();
      }
      return new AccessTokenClaims(
          UUID.fromString(claims.getSubject()),
          role,
          UUID.fromString(claims.getStringClaim("sid"))
      );
    } catch (ParseException | IllegalArgumentException | NullPointerException e) {
      throw InvalidAccessTokenException.withCause(e);
    }
  }

  @Override
  public RefreshTokenClaims parseRefreshToken(String token) {
    JWTClaimsSet claims = verifyAndParse(token, Instant.now(clock), refreshVerifier,
        InvalidRefreshTokenException::withNone, InvalidRefreshTokenException::withCause);
    try {
      return new RefreshTokenClaims(
          UUID.fromString(claims.getSubject()),
          UUID.fromString(claims.getStringClaim("sid")),
          UUID.fromString(claims.getJWTID())
      );
    } catch (ParseException | IllegalArgumentException | NullPointerException e) {
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

  private String sign(JWTClaimsSet claims, JWSSigner signer) {
    try {
      SignedJWT jwt = new SignedJWT(JWS_HEADER, claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("토큰 서명 실패", e);
    }
  }

  @Override
  public Duration getRefreshTokenTtl() {
    return Duration.ofDays(tokenProperties.refreshTokenExpirationDays());
  }

  private JWTClaimsSet verifyAndParse(String token, Instant now, JWSVerifier verifier,
      Supplier<? extends TokenException> invalidTokenSupplier,
      Function<Throwable, ? extends TokenException> invalidTokenFactory) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);

      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
        throw invalidTokenSupplier.get(); // 허용하지 않는 알고리즘
      }

      if (!jwt.verify(verifier)) {
        throw invalidTokenSupplier.get(); // 서명 불일치: 원인 예외가 없음
      }

      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date expiration = claims.getExpirationTime();
      if (expiration == null || !now.isBefore(expiration.toInstant())) {
        throw ExpiredTokenException.withNone(); // 정상 서명 + 만료: refresh 유도 대상
      }

      return claims;
    } catch (ParseException | JOSEException e) {
      throw invalidTokenFactory.apply(e); // 구조 깨짐/검증 오류: cause 보존
    }
  }
}
