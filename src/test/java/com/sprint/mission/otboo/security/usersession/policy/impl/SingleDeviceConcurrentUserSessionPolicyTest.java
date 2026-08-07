package com.sprint.mission.otboo.security.usersession.policy.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
class SingleDeviceConcurrentUserSessionPolicyTest {

  @Mock
  private UserSessionRegistry registry;

  private final SingleDeviceConcurrentUserSessionPolicy policy =
      new SingleDeviceConcurrentUserSessionPolicy();

  @Nested
  class Issue {

    @Test
    void 성공_registry의_issueExclusive를_호출하고_결과를_그대로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      UserSession expected = new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), now);
      given(registry.issueExclusive(userId, now)).willReturn(expected);

      // when
      UserSession result = policy.issue(userId, now, registry);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void 성공_issueExclusive만_호출하고_일반_issue는_호출하지_않는다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      given(registry.issueExclusive(userId, now))
          .willReturn(new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), now));

      // when
      policy.issue(userId, now, registry);

      // then
      verify(registry).issueExclusive(userId, now);
      verify(registry, never()).issue(any(UUID.class), any(Instant.class));
    }
  }
}
