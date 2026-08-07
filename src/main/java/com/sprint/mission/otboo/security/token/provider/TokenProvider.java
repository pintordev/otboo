package com.sprint.mission.otboo.security.token.provider;

import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import java.time.Instant;
import java.util.UUID;

public interface TokenProvider {

  String createAccessToken(UUID userId, UUID sessionId, String role, Instant now);

  String createRefreshToken(UUID userId, UUID sessionId, UUID jti, Instant now);

  AccessTokenClaims parseAccessToken(String token);

  RefreshTokenClaims parseRefreshToken(String token);
}
