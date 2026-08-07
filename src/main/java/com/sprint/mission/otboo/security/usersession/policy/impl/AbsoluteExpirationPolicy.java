package com.sprint.mission.otboo.security.usersession.policy.impl;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import java.time.Duration;
import java.time.Instant;

public class AbsoluteExpirationPolicy implements UserSessionExpirationPolicy {

  private final Duration ttl;

  public AbsoluteExpirationPolicy(Duration ttl) {
    this.ttl = ttl;
  }

  @Override
  public Instant expiresAtOnIssue(Instant issuedAt) {
    return issuedAt.plus(ttl);
  }

  @Override
  public Instant expiresAtOnRotate(UserSession current, Instant now) {
    return current.issuedAt().plus(ttl);
  }
}
