package com.sprint.mission.otboo.security.usersession.policy.impl;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.UUID;

/** 기기 수를 최대 maxDevices개로 제한한다. 넘치면 가장 오래된 세션부터 회수한다. */
public class MaxDeviceConcurrentUserSessionPolicy implements ConcurrentUserSessionPolicy {

  private final int maxDevices;

  public MaxDeviceConcurrentUserSessionPolicy(int maxDevices) {
    this.maxDevices = maxDevices;
  }

  @Override
  public UserSession issue(UUID userId, Instant now, UserSessionRegistry registry) {
    return registry.evictOldestAndIssue(userId, maxDevices, now);
  }
}
