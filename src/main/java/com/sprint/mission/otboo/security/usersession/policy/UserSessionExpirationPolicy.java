package com.sprint.mission.otboo.security.usersession.policy;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import java.time.Instant;

public interface UserSessionExpirationPolicy {

  Instant expiresAtOnIssue(Instant issuedAt);

  Instant expiresAtOnRotate(UserSession current, Instant now);
}
