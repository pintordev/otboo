package com.sprint.mission.otboo.security.usersession.policy.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbsoluteExpirationPolicyTest {

  private static final Duration TTL = Duration.ofDays(14);

  private final AbsoluteExpirationPolicy policy = new AbsoluteExpirationPolicy(TTL);

  @Nested
  class ExpiresAtOnIssue {

    @Test
    void 성공_issuedAt에_ttl을_더한_시각을_반환한다() {
      // given
      Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");

      // when
      Instant expiresAt = policy.expiresAtOnIssue(issuedAt);

      // then
      assertThat(expiresAt).isEqualTo(issuedAt.plus(TTL));
    }
  }

  @Nested
  class ExpiresAtOnRotate {

    @Test
    void 성공_now가_아니라_최초_issuedAt에_ttl을_더한_시각을_반환한다() {
      // given
      Instant originalIssuedAt = Instant.parse("2026-01-01T00:00:00Z");
      UserSession current = new UserSession(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), originalIssuedAt);
      Instant now = originalIssuedAt.plus(Duration.ofDays(10));

      // when
      Instant expiresAt = policy.expiresAtOnRotate(current, now);

      // then
      assertThat(expiresAt).isEqualTo(originalIssuedAt.plus(TTL));
      assertThat(expiresAt).isNotEqualTo(now.plus(TTL));
    }
  }
}
