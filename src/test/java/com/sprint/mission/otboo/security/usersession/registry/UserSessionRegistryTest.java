package com.sprint.mission.otboo.security.usersession.registry;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.RefreshTokenReusedException;
import com.sprint.mission.otboo.security.usersession.exception.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserSessionRegistryTest {

  private UserSessionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = mock(UserSessionRegistry.class, CALLS_REAL_METHODS);
  }

  @Nested
  @DisplayName("issue")
  class Issue {

    @Test
    @DisplayName("새 세션을 만들어 save로 저장하고 저장된 세션을 반환한다")
    void issue_savesNewSessionAndReturnsIt() {
      UUID userId = UUID.randomUUID();
      Instant now = Instant.now();
      Duration ttl = Duration.ofDays(14);
      given(registry.save(eq(userId), any(UserSession.class), eq(now.plus(ttl))))
          .willAnswer(invocation -> invocation.getArgument(1));

      UserSession result = registry.issue(userId, now, ttl);

      assertThat(result.issuedAt()).isEqualTo(now);
      verify(registry).save(eq(userId), any(UserSession.class), eq(now.plus(ttl)));
    }
  }

  @Nested
  @DisplayName("rotate")
  class Rotate {

    @Test
    @DisplayName("정상 회전 시 sessionId와 issuedAt은 유지하고 refreshJti만 새로 발급해 저장한다")
    void rotate_validToken_keepsSessionIdAndIssuedAtButIssuesNewJti() {
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID currentJti = UUID.randomUUID();
      Instant issuedAt = Instant.now().minus(3, ChronoUnit.DAYS);
      UserSession current = new UserSession(currentJti, sessionId, issuedAt);
      Duration ttl = Duration.ofDays(14);
      given(registry.find(userId)).willReturn(Optional.of(current));
      given(registry.save(eq(userId), any(UserSession.class), any(Instant.class)))
          .willAnswer(invocation -> invocation.getArgument(1));

      UserSession result = registry.rotate(userId, sessionId, currentJti, ttl);

      assertThat(result.sessionId()).isEqualTo(sessionId);
      assertThat(result.issuedAt()).isEqualTo(issuedAt);
      assertThat(result.currentRefreshJti()).isNotEqualTo(currentJti);
      verify(registry).save(userId, result, issuedAt.plus(ttl));
    }

    @Test
    @DisplayName("세션 자체가 없으면 UserSessionExpiredException을 던진다")
    void rotate_noSession_throwsUserSessionExpiredException() {
      UUID userId = UUID.randomUUID();
      given(registry.find(userId)).willReturn(Optional.empty());

      assertThatThrownBy(() ->
          registry.rotate(userId, UUID.randomUUID(), UUID.randomUUID(), Duration.ofDays(14)))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("sessionId가 다르면(다른 기기 재로그인) UserSessionExpiredException을 던진다")
    void rotate_sessionIdMismatch_throwsUserSessionExpiredException() {
      UUID userId = UUID.randomUUID();
      UserSession current = new UserSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
      given(registry.find(userId)).willReturn(Optional.of(current));

      assertThatThrownBy(() -> registry.rotate(
          userId, UUID.randomUUID(), current.currentRefreshJti(), Duration.ofDays(14)))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("이미 사용된(탈취 의심) refreshJti로 재시도하면 RefreshTokenReusedException을 던지고 세션을 폐기한다")
    void rotate_reusedRefreshJti_throwsAndRevokesSession() {
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UserSession current = new UserSession(UUID.randomUUID(), sessionId, Instant.now());
      given(registry.find(userId)).willReturn(Optional.of(current));

      assertThatThrownBy(() ->
          registry.rotate(userId, sessionId, UUID.randomUUID(), Duration.ofDays(14)))
          .isInstanceOf(RefreshTokenReusedException.class);

      verify(registry).revoke(userId);
    }
  }

  @Nested
  @DisplayName("verifyUserSession")
  class VerifyUserSession {

    @Test
    @DisplayName("sessionId가 일치하면 세션을 반환한다")
    void verifyUserSession_matchingSessionId_returnsSession() {
      UUID userId = UUID.randomUUID();
      UserSession current = new UserSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
      given(registry.find(userId)).willReturn(Optional.of(current));

      UserSession result = registry.verifyUserSession(userId, current.sessionId());

      assertThat(result).isEqualTo(current);
    }

    @Test
    @DisplayName("세션이 없으면 UserSessionExpiredException을 던진다")
    void verifyUserSession_noSession_throwsUserSessionExpiredException() {
      UUID userId = UUID.randomUUID();
      given(registry.find(userId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> registry.verifyUserSession(userId, UUID.randomUUID()))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("sessionId가 다르면(다른 기기 재로그인) UserSessionExpiredException을 던진다")
    void verifyUserSession_sessionIdMismatch_throwsUserSessionExpiredException() {
      UUID userId = UUID.randomUUID();
      UserSession current = new UserSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
      given(registry.find(userId)).willReturn(Optional.of(current));

      assertThatThrownBy(() -> registry.verifyUserSession(userId, UUID.randomUUID()))
          .isInstanceOf(UserSessionExpiredException.class);
    }
  }
}
