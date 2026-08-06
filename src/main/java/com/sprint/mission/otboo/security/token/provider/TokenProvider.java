package com.sprint.mission.otboo.security.token.provider;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface TokenProvider {

  String createAccessToken(UUID userId, String role, UUID sessionId, Instant now);

  String createRefreshToken(UUID userId, UUID jti, UUID sessionId, Instant now, Instant expiresAt);

  AccessTokenClaims parseAccessToken(String token);

  RefreshTokenClaims parseRefreshToken(String token);

  Instant getAccessTokenExpiresAt(Instant from);

  Instant getRefreshTokenExpiresAt(Instant from);

  Duration getRefreshTokenTtl();
}
