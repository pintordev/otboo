package com.sprint.mission.otboo.security.usersession.policy.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiDeviceConcurrentUserSessionPolicyTest {

  @Mock
  private UserSessionRegistry registry;

  private final MultiDeviceConcurrentUserSessionPolicy policy =
      new MultiDeviceConcurrentUserSessionPolicy();

  @Nested
  class Issue {

    @Test
    void 성공_registry의_issue를_호출하고_결과를_그대로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      UserSession expected = new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), now);
      given(registry.issue(userId, now)).willReturn(expected);

      // when
      UserSession result = policy.issue(userId, now, registry);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void 성공_기존_세션을_전혀_건드리지_않는다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      given(registry.issue(userId, now))
          .willReturn(new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), now));

      // when
      policy.issue(userId, now, registry);

      // then
      verify(registry).issue(userId, now);
      verify(registry, never()).issueExclusive(any(UUID.class), any(Instant.class));
      verify(registry, never()).revoke(any(UUID.class), any(UUID.class));
      verify(registry, never()).revokeAll(any(UUID.class));
      verify(registry, never()).findAllByUserId(any(UUID.class));
      verifyNoMoreInteractions(registry);
    }
  }
}
