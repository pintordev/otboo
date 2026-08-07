package com.sprint.mission.otboo.security.usersession.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserSessionTest {

  @Nested
  class Issue {

    @Test
    void 성공_userId와_issuedAt이_그대로_반영된다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");

      // when
      UserSession issued = UserSession.issue(userId, now);

      // then
      assertThat(issued.userId()).isEqualTo(userId);
      assertThat(issued.issuedAt()).isEqualTo(now);
    }

    @Test
    void 성공_호출할_때마다_다른_sessionId와_currentRefreshJti가_생성된다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");

      // when
      UserSession first = UserSession.issue(userId, now);
      UserSession second = UserSession.issue(userId, now);

      // then
      assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
      assertThat(first.currentRefreshJti()).isNotEqualTo(second.currentRefreshJti());
    }
  }

  @Nested
  class Rotate {

    @Test
    void 성공_userId_sessionId_issuedAt은_유지된다() {
      // given
      UserSession original = new UserSession(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          Instant.parse("2026-01-01T00:00:00Z"));

      // when
      UserSession rotated = original.rotate();

      // then
      assertThat(rotated.userId()).isEqualTo(original.userId());
      assertThat(rotated.sessionId()).isEqualTo(original.sessionId());
      assertThat(rotated.issuedAt()).isEqualTo(original.issuedAt());
    }

    @Test
    void 성공_currentRefreshJti만_새로_생성된다() {
      // given
      UserSession original = new UserSession(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          Instant.parse("2026-01-01T00:00:00Z"));

      // when
      UserSession rotated = original.rotate();

      // then
      assertThat(rotated.currentRefreshJti()).isNotEqualTo(original.currentRefreshJti());
    }
  }
}
