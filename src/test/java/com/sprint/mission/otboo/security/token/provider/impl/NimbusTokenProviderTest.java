package com.sprint.mission.otboo.security.token.provider.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sprint.mission.otboo.security.token.exception.business.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.AbstractTokenProviderContractTest;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NimbusTokenProviderTest extends AbstractTokenProviderContractTest {

  @Override
  protected TokenProvider createProvider(TokenProperties properties, Clock clock) {
    return new NimbusTokenProvider(properties, clock);
  }

  @Override
  protected String craftToken(String base64Secret, String subject, String sid, String role, String jti,
      Instant issuedAt, Instant expiration) {
    try {
      JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
          .issueTime(Date.from(issuedAt))
          .expirationTime(Date.from(expiration));
      if (subject != null) {
        claimsBuilder.subject(subject);
      }
      if (sid != null) {
        claimsBuilder.claim("sid", sid);
      }
      if (role != null) {
        claimsBuilder.claim("role", role);
      }
      if (jti != null) {
        claimsBuilder.jwtID(jti);
      }
      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
      SignedJWT jwt = new SignedJWT(header, claimsBuilder.build());
      jwt.sign(new MACSigner(Base64.getDecoder().decode(base64Secret)));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  // NimbusTokenProvider는 jjwt와 달리 헤더의 alg를 명시적으로 HS256과 비교해서 걸러낸다 (verifyAndParse 참고).
  @Nested
  class 알고리즘_검증 {

    @Test
    void 실패_HS256이_아닌_알고리즘으로_서명된_토큰이면_InvalidAccessTokenException이_발생한다() throws JOSEException {
      // given
      TokenProvider provider = createProvider(
          new TokenProperties(ACCESS_EXPIRATION, REFRESH_EXPIRATION, ACCESS_SECRET, REFRESH_SECRET),
          Clock.fixed(NOW, ZoneOffset.UTC));
      String token = signWithHs384(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ROLE_USER", null);

      // when & then
      assertThatThrownBy(() -> provider.parseAccessToken(token))
          .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 실패_HS256이_아닌_알고리즘으로_서명된_토큰이면_InvalidRefreshTokenException이_발생한다() throws JOSEException {
      // given
      TokenProvider provider = createProvider(
          new TokenProperties(ACCESS_EXPIRATION, REFRESH_EXPIRATION, ACCESS_SECRET, REFRESH_SECRET),
          Clock.fixed(NOW, ZoneOffset.UTC));
      String token = signWithHs384(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null,
          UUID.randomUUID().toString());

      // when & then
      assertThatThrownBy(() -> provider.parseRefreshToken(token))
          .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private String signWithHs384(String subject, String sid, String role, String jti) throws JOSEException {
      byte[] hs384Secret = new byte[48];
      Arrays.fill(hs384Secret, (byte) 7);
      JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
          .subject(subject)
          .claim("sid", sid)
          .issueTime(Date.from(NOW))
          .expirationTime(Date.from(NOW.plus(ACCESS_EXPIRATION)));
      if (role != null) {
        claimsBuilder.claim("role", role);
      }
      if (jti != null) {
        claimsBuilder.jwtID(jti);
      }
      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS384).type(JOSEObjectType.JWT).build();
      SignedJWT jwt = new SignedJWT(header, claimsBuilder.build());
      jwt.sign(new MACSigner(hs384Secret));
      return jwt.serialize();
    }
  }
}
