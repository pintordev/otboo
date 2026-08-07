package com.sprint.mission.otboo.security.usersession.policy.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaxDeviceConcurrentUserSessionPolicyTest {

  private static final int MAX_DEVICES = 3;

  @Mock
  private UserSessionRegistry registry;

  private final MaxDeviceConcurrentUserSessionPolicy policy =
      new MaxDeviceConcurrentUserSessionPolicy(MAX_DEVICES);

  private static UserSession sessionAt(UUID userId, Instant issuedAt) {
    return new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), issuedAt);
  }

  @Nested
  class Issue {

    @Test
    void 성공_세션수가_maxDevices보다_적으면_회수없이_발급만_한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-10T00:00:00Z");
      List<UserSession> existing = List.of(
          sessionAt(userId, now.minusSeconds(20)),
          sessionAt(userId, now.minusSeconds(10)));
      given(registry.findAllByUserId(userId)).willReturn(existing);
      given(registry.issue(userId, now)).willReturn(sessionAt(userId, now));

      // when
      policy.issue(userId, now, registry);

      // then
      verify(registry, never()).revoke(any(UUID.class), any(UUID.class));
      verify(registry).issue(userId, now);
    }

    @Test
    void 성공_세션수가_정확히_maxDevices면_가장_오래된_세션_1개만_회수하고_발급한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-10T00:00:00Z");
      UserSession oldest = sessionAt(userId, now.minusSeconds(30));
      UserSession middle = sessionAt(userId, now.minusSeconds(20));
      UserSession newest = sessionAt(userId, now.minusSeconds(10));
      given(registry.findAllByUserId(userId)).willReturn(List.of(newest, oldest, middle));
      given(registry.issue(userId, now)).willReturn(sessionAt(userId, now));

      // when
      policy.issue(userId, now, registry);

      // then
      verify(registry).revoke(userId, oldest.sessionId());
      verify(registry, never()).revoke(userId, middle.sessionId());
      verify(registry, never()).revoke(userId, newest.sessionId());
      verify(registry).issue(userId, now);
    }

    @Test
    void 성공_세션수가_maxDevices를_초과하면_초과분과_여유분을_합쳐_회수하고_발급한다() {
      // given: maxDevices=3인데 이미 4개 -> 4-3+1=2개(가장 오래된 두 개) 회수해야 발급 후 3개가 된다
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-10T00:00:00Z");
      UserSession oldest = sessionAt(userId, now.minusSeconds(40));
      UserSession second = sessionAt(userId, now.minusSeconds(30));
      UserSession third = sessionAt(userId, now.minusSeconds(20));
      UserSession newest = sessionAt(userId, now.minusSeconds(10));
      given(registry.findAllByUserId(userId)).willReturn(List.of(newest, third, oldest, second));
      given(registry.issue(userId, now)).willReturn(sessionAt(userId, now));

      // when
      policy.issue(userId, now, registry);

      // then
      verify(registry).revoke(userId, oldest.sessionId());
      verify(registry).revoke(userId, second.sessionId());
      verify(registry, never()).revoke(userId, third.sessionId());
      verify(registry, never()).revoke(userId, newest.sessionId());
      verify(registry).issue(userId, now);
    }

    @Test
    void 성공_발급된_세션을_그대로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-10T00:00:00Z");
      given(registry.findAllByUserId(userId)).willReturn(List.of());
      UserSession issued = sessionAt(userId, now);
      given(registry.issue(userId, now)).willReturn(issued);

      // when
      UserSession result = policy.issue(userId, now, registry);

      // then
      assertThat(result).isEqualTo(issued);
    }
  }
}
