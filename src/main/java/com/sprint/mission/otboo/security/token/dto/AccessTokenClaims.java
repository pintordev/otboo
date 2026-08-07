package com.sprint.mission.otboo.security.token.dto;

import java.util.UUID;

public record AccessTokenClaims(
    UUID userId,
    UUID sessionId,
    String role
) {

}
