package com.sprint.mission.otboo.security.token.provider.impl;

import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.AbstractTokenProviderContractTest;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

class JjwtTokenProviderTest extends AbstractTokenProviderContractTest {

  @Override
  protected TokenProvider createProvider(TokenProperties properties, Clock clock) {
    return new JjwtTokenProvider(properties, clock);
  }

  @Override
  protected String craftToken(String base64Secret, String subject, String sid, String role, String jti,
      Instant issuedAt, Instant expiration) {
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    JwtBuilder builder = Jwts.builder();
    if (subject != null) {
      builder.subject(subject);
    }
    if (sid != null) {
      builder.claim("sid", sid);
    }
    if (role != null) {
      builder.claim("role", role);
    }
    if (jti != null) {
      builder.id(jti);
    }
    return builder
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiration))
        .signWith(key)
        .compact();
  }
}
