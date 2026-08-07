package com.sprint.mission.otboo.security.usersession.policy.impl;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.UUID;

/** 다중 기기 로그인: 기존 세션 수에 제한을 두지 않는다. */
public class MultiDeviceConcurrentUserSessionPolicy implements ConcurrentUserSessionPolicy {

  @Override
  public UserSession issue(UUID userId, Instant now, UserSessionRegistry registry) {
    return registry.issue(userId, now);
  }
}
