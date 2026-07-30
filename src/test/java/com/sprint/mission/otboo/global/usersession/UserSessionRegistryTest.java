package com.sprint.mission.otboo.global.usersession;

import com.sprint.mission.otboo.global.usersession.exception.RefreshTokenReusedException;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataRedisTest
@ActiveProfiles("test")
class UserSessionRegistryTest {

  @Container
  static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired
  private StringRedisTemplate redisTemplate;

  private UserSessionRegistry userSessionRegistry;

  @BeforeEach
  void setUp() {
    userSessionRegistry = new UserSessionRegistry(redisTemplate);
  }

  @Nested
  @DisplayName("issue")
  class Issue {

    @Test
    @DisplayName("호출할 때마다 서로 다른 sessionId/refreshJti를 발급한다")
    void issue_generatesUniqueIdentifiersEachCall() {
      UserSession first = userSessionRegistry.issue();
      UserSession second = userSessionRegistry.issue();

      assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
      assertThat(first.currentRefreshJti()).isNotEqualTo(second.currentRefreshJti());
    }
  }

  @Nested
  @DisplayName("save / find")
  class SaveAndFind {

    @Test
    @DisplayName("저장한 세션을 find로 그대로 조회할 수 있다")
    void save_thenFind_returnsSameSession() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession session = userSessionRegistry.issue();
      Instant expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);

      // when
      userSessionRegistry.save(userId, session, expiresAt);
      Optional<UserSession> found = userSessionRegistry.find(userId);

      // then
      assertThat(found).isPresent();
      assertThat(found.get().sessionId()).isEqualTo(session.sessionId());
      assertThat(found.get().currentRefreshJti()).isEqualTo(session.currentRefreshJti());
    }

    @Test
    @DisplayName("존재하지 않는 유저의 세션을 조회하면 빈 Optional을 반환한다")
    void find_noSession_returnsEmpty() {
      Optional<UserSession> found = userSessionRegistry.find(UUID.randomUUID());

      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save는 전달받은 만료 시각으로 Redis 키의 TTL을 설정한다")
    void save_setsExpirationOnRedisKey() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession session = userSessionRegistry.issue();
      Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

      // when
      userSessionRegistry.save(userId, session, expiresAt);
      Long ttlSeconds = redisTemplate.getExpire("auth:user-session:" + userId);

      // then
      assertThat(ttlSeconds).isNotNull();
      assertThat(ttlSeconds).isGreaterThan(0)
          .isLessThanOrEqualTo(Duration.ofMinutes(10).toSeconds());
    }
  }

  @Nested
  @DisplayName("rotate")
  class Rotate {

    @Test
    @DisplayName("정상 회전 시 sessionId는 유지하고 refreshJti만 새로 발급하며, issuedAt(최초 로그인 시각/절대 만료 기준)은 그대로 유지한다")
    void rotate_validToken_keepsSessionIdAndOriginalIssuedAt() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession original = userSessionRegistry.issue();
      userSessionRegistry.save(userId, original, Instant.now().plus(14, ChronoUnit.DAYS));

      // when
      UserSession rotated = userSessionRegistry.rotate(userId, original.sessionId(),
          original.currentRefreshJti());

      // then
      assertThat(rotated.sessionId()).isEqualTo(original.sessionId());
      assertThat(rotated.currentRefreshJti()).isNotEqualTo(original.currentRefreshJti());
      assertThat(rotated.issuedAt()).isEqualTo(original.issuedAt());
    }

    @Test
    @DisplayName("세션 자체가 없으면 UserSessionExpiredException을 던진다")
    void rotate_noSession_throwsUserSessionExpiredException() {
      UUID userId = UUID.randomUUID();

      assertThatThrownBy(
          () -> userSessionRegistry.rotate(userId, UUID.randomUUID(), UUID.randomUUID()))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("다른 기기에서 재로그인해 sessionId가 바뀐 경우 UserSessionExpiredException을 던진다")
    void rotate_sessionIdMismatch_throwsUserSessionExpiredException() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession original = userSessionRegistry.issue();
      userSessionRegistry.save(userId, original, Instant.now().plus(14, ChronoUnit.DAYS));

      UUID staleSessionId = UUID.randomUUID();

      // when & then
      assertThatThrownBy(
          () -> userSessionRegistry.rotate(userId, staleSessionId, original.currentRefreshJti()))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("이미 사용된(탈취 의심) refreshJti로 재시도하면 RefreshTokenReusedException을 던지고 세션을 폐기한다")
    void rotate_reusedRefreshJti_throwsAndRevokesSession() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession original = userSessionRegistry.issue();
      userSessionRegistry.save(userId, original, Instant.now().plus(14, ChronoUnit.DAYS));

      UUID staleJti = UUID.randomUUID();

      // when & then
      assertThatThrownBy(() -> userSessionRegistry.rotate(userId, original.sessionId(), staleJti))
          .isInstanceOf(RefreshTokenReusedException.class);

      assertThat(userSessionRegistry.find(userId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("verifyLoginSession")
  class VerifyLoginSession {

    @Test
    @DisplayName("access 토큰의 sid가 현재 세션과 일치하면 세션을 반환한다")
    void verifyLoginSession_matchingSid_returnsSession() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession session = userSessionRegistry.issue();
      userSessionRegistry.save(userId, session, Instant.now().plus(14, ChronoUnit.DAYS));

      // when
      UserSession verified = userSessionRegistry.verifyLoginSession(userId, session.sessionId());

      // then
      assertThat(verified.sessionId()).isEqualTo(session.sessionId());
    }

    @Test
    @DisplayName("세션이 없으면 UserSessionExpiredException을 던진다")
    void verifyLoginSession_noSession_throwsUserSessionExpiredException() {
      assertThatThrownBy(
          () -> userSessionRegistry.verifyLoginSession(UUID.randomUUID(), UUID.randomUUID()))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    @DisplayName("다른 기기에서 재로그인해 sid가 바뀐 경우(단일 로그인 강제) UserSessionExpiredException을 던진다")
    void verifyLoginSession_sidMismatch_throwsUserSessionExpiredException() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession session = userSessionRegistry.issue();
      userSessionRegistry.save(userId, session, Instant.now().plus(14, ChronoUnit.DAYS));

      UUID staleSid = UUID.randomUUID();

      // when & then
      assertThatThrownBy(() -> userSessionRegistry.verifyLoginSession(userId, staleSid))
          .isInstanceOf(UserSessionExpiredException.class);
    }
  }

  @Nested
  @DisplayName("revoke")
  class Revoke {

    @Test
    @DisplayName("세션을 폐기하면 이후 find는 빈 Optional을 반환한다")
    void revoke_thenFind_returnsEmpty() {
      // given
      UUID userId = UUID.randomUUID();
      UserSession session = userSessionRegistry.issue();
      userSessionRegistry.save(userId, session, Instant.now().plus(14, ChronoUnit.DAYS));

      // when
      userSessionRegistry.revoke(userId);

      // then
      assertThat(userSessionRegistry.find(userId)).isEmpty();
    }
  }
}
