package com.sprint.mission.otboo.global.usersession;

import java.time.Instant;
import java.util.UUID;

public record UserSession(
    UUID sessionId,
    UUID currentRefreshJti,
    Instant issuedAt
) {

}
