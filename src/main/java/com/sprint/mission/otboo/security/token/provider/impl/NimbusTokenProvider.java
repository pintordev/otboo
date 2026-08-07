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
import com.sprint.mission.otboo.security.token.exception.business.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.exception.business.TokenException;
import com.sprint.mission.otboo.security.token.exception.system.TokenProviderException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class NimbusTokenProvider implements TokenProvider {

  // 알고리즘 정책
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
      throw TokenProviderException.withMessageAndCause("토큰 키 초기화 실패", e);
    } catch (IllegalArgumentException e) {
      // Base64.getDecoder().decode()가 잘못된 base64 형식일 때 던짐
      throw TokenProviderException.withMessageAndCause("토큰 시크릿 base64 디코딩 실패", e);
    }
  }

  @Override
  public String createAccessToken(UUID userId, UUID sessionId, String role, Instant now) {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(userId.toString())
        .claim("sid", sessionId.toString())
        .claim("role", role)
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(tokenProperties.accessTokenExpiration())))
        .build();
    return sign(claims, accessSigner);
  }

  @Override
  public String createRefreshToken(UUID userId, UUID sessionId, UUID jti, Instant now) {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(userId.toString())
        .claim("sid", sessionId.toString())
        .jwtID(jti.toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(tokenProperties.refreshTokenExpiration())))
        .build();
    return sign(claims, refreshSigner);
  }

  @Override
  public AccessTokenClaims parseAccessToken(String token) {
    JWTClaimsSet claims = verifyAndParse(token, Instant.now(clock), accessVerifier,
        InvalidAccessTokenException::withNone, InvalidAccessTokenException::withCause);
    try {
      String subject = claims.getSubject();
      String sid = claims.getStringClaim("sid");
      String role = claims.getStringClaim("role");

      if (subject == null || sid == null || role == null || role.isBlank()) {
        throw InvalidAccessTokenException.withNone();
      }

      return new AccessTokenClaims(
          UUID.fromString(subject),
          UUID.fromString(sid),
          role
      );
    } catch (ParseException | IllegalArgumentException e) {
      throw InvalidAccessTokenException.withCause(e);
    }
  }

  @Override
  public RefreshTokenClaims parseRefreshToken(String token) {
    JWTClaimsSet claims = verifyAndParse(token, Instant.now(clock), refreshVerifier,
        InvalidRefreshTokenException::withNone, InvalidRefreshTokenException::withCause);
    try {

      String subject = claims.getSubject();
      String sid = claims.getStringClaim("sid");
      String jti = claims.getJWTID();

      if (subject == null || sid == null || jti == null) {
        throw InvalidRefreshTokenException.withNone();
      }

      return new RefreshTokenClaims(
          UUID.fromString(subject),
          UUID.fromString(sid),
          UUID.fromString(jti)
      );
    } catch (ParseException | IllegalArgumentException e) {
      throw InvalidRefreshTokenException.withCause(e);
    }
  }

  private String sign(JWTClaimsSet claims, JWSSigner signer) {
    try {
      SignedJWT jwt = new SignedJWT(JWS_HEADER, claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException e) {
      throw TokenProviderException.withMessageAndCause("토큰 서명 실패", e);
    }
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
