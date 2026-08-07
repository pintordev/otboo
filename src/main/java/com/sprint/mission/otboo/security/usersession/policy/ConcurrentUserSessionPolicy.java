package com.sprint.mission.otboo.security.usersession.policy;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.UUID;

// 기존 세션 처리 전략. registry.issue()/issueExclusive() 중 무엇을 어떻게 호출할지는 정책이 결정한다.
public interface ConcurrentUserSessionPolicy {

  UserSession issue(UUID userId, Instant now, UserSessionRegistry registry);
}
