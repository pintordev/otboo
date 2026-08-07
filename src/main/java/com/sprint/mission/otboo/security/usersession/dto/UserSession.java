package com.sprint.mission.otboo.security.usersession.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSession(
    UUID userId,
    UUID sessionId,
    UUID currentRefreshJti,
    Instant issuedAt
) {

  public static UserSession issue(UUID userId, Instant now) {
    return new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), now);
  }

  public UserSession rotate() {
    return new UserSession(userId, sessionId, UUID.randomUUID(), issuedAt);
  }
}
