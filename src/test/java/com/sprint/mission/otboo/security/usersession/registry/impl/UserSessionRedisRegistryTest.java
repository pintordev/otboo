package com.sprint.mission.otboo.security.usersession.registry.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.business.RefreshTokenReusedException;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.policy.impl.SlidingExpirationPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// Redis 명령/Lua 스크립트가 실제로 기대한 대로 동작하는지는 실제 Redis 없이는 검증할 수 없어 Testcontainers를 쓴다.
class UserSessionRedisRegistryTest implements RedisTestContainerSupport {

  private static final Duration TTL = Duration.ofMinutes(30);
  // Redis EXPIREAT는 Redis 서버의 실제 시스템 시계로 평가되므로, 여기서만 쓰는 논리적 시각이 아니라
  // 테스트 실행 시점의 실제 현재 시각에 가까워야 한다 (과거 시각을 주면 Redis가 즉시 키를 지운다).
  private static final Instant NOW = Instant.now();

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private UserSessionRedisRegistry registry;

  @BeforeAll
  static void setUpRedis() {
    connectionFactory = new LettuceConnectionFactory(
        REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void tearDownRedis() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    registry = new UserSessionRedisRegistry(redisTemplate,
        new SlidingExpirationPolicy(TTL), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static UUID newUserId() {
    return UUID.randomUUID();
  }

  private static UserSession newSession(UUID userId, Instant issuedAt) {
    return new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), issuedAt);
  }

  @Nested
  class Save {

    @Test
    void 성공_저장한_세션을_find로_그대로_조회할_수_있다() {
      // given
      UserSession session = newSession(newUserId(), NOW);

      // when
      registry.save(session, NOW.plus(TTL));

      // then
      assertThat(registry.find(session.userId(), session.sessionId())).contains(session);
    }

    @Test
    void 성공_findAllByUserId에서도_조회된다() {
      // given
      UUID userId = newUserId();
      UserSession session = newSession(userId, NOW);

      // when
      registry.save(session, NOW.plus(TTL));

      // then
      assertThat(registry.findAllByUserId(userId)).containsExactly(session);
    }

    @Test
    void 성공_세션_키에_만료시각이_설정된다() {
      // given
      UUID userId = newUserId();
      UserSession session = newSession(userId, NOW);

      // when
      registry.save(session, NOW.plus(TTL));

      // then
      Long ttlMillis = redisTemplate.getExpire(
          "auth:user-session:" + userId + ":" + session.sessionId(), TimeUnit.MILLISECONDS);
      assertThat(ttlMillis).isPositive();
      assertThat(ttlMillis).isLessThanOrEqualTo(TTL.toMillis());
    }
  }

  @Nested
  class Find {

    @Test
    void 성공_존재하는_세션을_반환한다() {
      // given
      UUID userId = newUserId();
      UserSession session = newSession(userId, NOW);
      registry.save(session, NOW.plus(TTL));

      // when
      Optional<UserSession> found = registry.find(userId, session.sessionId());

      // then
      assertThat(found).contains(session);
    }

    @Test
    void 실패_존재하지_않으면_빈_Optional을_반환한다() {
      // given
      UUID userId = newUserId();

      // when
      Optional<UserSession> found = registry.find(userId, UUID.randomUUID());

      // then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  class FindAllByUserId {

    @Test
    void 성공_여러_세션을_전부_반환한다() {
      // given
      UUID userId = newUserId();
      UserSession s1 = newSession(userId, NOW);
      UserSession s2 = newSession(userId, NOW.plusSeconds(1));
      registry.save(s1, NOW.plus(TTL));
      registry.save(s2, NOW.plus(TTL).plusSeconds(1));

      // when
      List<UserSession> sessions = registry.findAllByUserId(userId);

      // then
      assertThat(sessions).containsExactlyInAnyOrder(s1, s2);
    }

    @Test
    void 성공_세션이_없으면_빈_리스트를_반환한다() {
      // given
      UUID userId = newUserId();

      // when
      List<UserSession> sessions = registry.findAllByUserId(userId);

      // then
      assertThat(sessions).isEmpty();
    }

    @Test
    void 성공_다른_유저의_세션은_섞이지_않는다() {
      // given
      UUID userId = newUserId();
      UUID otherUserId = newUserId();
      UserSession mine = newSession(userId, NOW);
      UserSession other = newSession(otherUserId, NOW);
      registry.save(mine, NOW.plus(TTL));
      registry.save(other, NOW.plus(TTL));

      // when
      List<UserSession> sessions = registry.findAllByUserId(userId);

      // then
      assertThat(sessions).containsExactly(mine);
    }
  }

  @Nested
  class Revoke {

    @Test
    void 성공_해당_세션만_삭제되고_다른_세션은_남는다() {
      // given
      UUID userId = newUserId();
      UserSession target = newSession(userId, NOW);
      UserSession other = newSession(userId, NOW);
      registry.save(target, NOW.plus(TTL));
      registry.save(other, NOW.plus(TTL));

      // when
      registry.revoke(userId, target.sessionId());

      // then
      assertThat(registry.find(userId, target.sessionId())).isEmpty();
      assertThat(registry.findAllByUserId(userId)).containsExactly(other);
    }
  }

  @Nested
  class RevokeAll {

    @Test
    void 성공_이_유저의_모든_세션이_삭제된다() {
      // given
      UUID userId = newUserId();
      UserSession s1 = newSession(userId, NOW);
      UserSession s2 = newSession(userId, NOW);
      registry.save(s1, NOW.plus(TTL));
      registry.save(s2, NOW.plus(TTL));

      // when
      registry.revokeAll(userId);

      // then
      assertThat(registry.findAllByUserId(userId)).isEmpty();
      assertThat(registry.find(userId, s1.sessionId())).isEmpty();
      assertThat(registry.find(userId, s2.sessionId())).isEmpty();
    }

    @Test
    void 성공_다른_유저의_세션은_영향받지_않는다() {
      // given
      UUID userId = newUserId();
      UUID otherUserId = newUserId();
      UserSession mine = newSession(userId, NOW);
      UserSession other = newSession(otherUserId, NOW);
      registry.save(mine, NOW.plus(TTL));
      registry.save(other, NOW.plus(TTL));

      // when
      registry.revokeAll(userId);

      // then
      assertThat(registry.find(otherUserId, other.sessionId())).contains(other);
    }
  }

  @Nested
  class ReplaceAll {

    @Test
    void 성공_기존_세션을_전부_지우고_새_세션만_남긴다() {
      // given
      UUID userId = newUserId();
      UserSession old1 = newSession(userId, NOW);
      UserSession old2 = newSession(userId, NOW);
      registry.save(old1, NOW.plus(TTL));
      registry.save(old2, NOW.plus(TTL));
      UserSession fresh = newSession(userId, NOW);

      // when
      UserSession result = registry.replaceAll(fresh, NOW.plus(TTL));

      // then
      assertThat(result).isEqualTo(fresh);
      assertThat(registry.findAllByUserId(userId)).containsExactly(fresh);
      assertThat(registry.find(userId, old1.sessionId())).isEmpty();
      assertThat(registry.find(userId, old2.sessionId())).isEmpty();
    }

    @Test
    void 성공_기존_세션이_없어도_정상적으로_저장된다() {
      // given
      UUID userId = newUserId();
      UserSession fresh = newSession(userId, NOW);

      // when
      registry.replaceAll(fresh, NOW.plus(TTL));

      // then
      assertThat(registry.find(userId, fresh.sessionId())).contains(fresh);
    }
  }

  @Nested
  class CompareAndRotate {

    @Test
    void 성공_refreshJti가_일치하면_새_jti로_교체된다() {
      // given
      UUID userId = newUserId();
      UserSession session = newSession(userId, NOW);
      registry.save(session, NOW.plus(TTL));
      UUID newJti = UUID.randomUUID();

      // when
      UserSession rotated = registry.compareAndRotate(userId, session.sessionId(),
          session.currentRefreshJti(), newJti, session.issuedAt(), NOW.plus(TTL).plusSeconds(1));

      // then
      assertThat(rotated.currentRefreshJti()).isEqualTo(newJti);
      assertThat(rotated.sessionId()).isEqualTo(session.sessionId());
      assertThat(registry.find(userId, session.sessionId()).orElseThrow().currentRefreshJti())
          .isEqualTo(newJti);
    }

    @Test
    void 실패_세션이_없으면_UserSessionExpiredException이_발생한다() {
      // given
      UUID userId = newUserId();

      // when & then
      assertThatThrownBy(() -> registry.compareAndRotate(userId, UUID.randomUUID(),
          UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plus(TTL)))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    void 실패_refreshJti가_다르면_RefreshTokenReusedException이_발생하고_모든_세션이_삭제된다() {
      // given
      UUID userId = newUserId();
      UserSession session = newSession(userId, NOW);
      registry.save(session, NOW.plus(TTL));
      UUID staleJti = UUID.randomUUID();

      // when & then
      assertThatThrownBy(() -> registry.compareAndRotate(userId, session.sessionId(),
          staleJti, UUID.randomUUID(), session.issuedAt(), NOW.plus(TTL)))
          .isInstanceOf(RefreshTokenReusedException.class);

      assertThat(registry.findAllByUserId(userId)).isEmpty();
      assertThat(registry.find(userId, session.sessionId())).isEmpty();
    }
  }

  @Nested
  class IssueDefaultMethod {

    @Test
    void 성공_새_세션을_발급하고_저장까지_한다() {
      // given
      UUID userId = newUserId();

      // when
      UserSession issued = registry.issue(userId, NOW);

      // then
      assertThat(issued.userId()).isEqualTo(userId);
      assertThat(registry.find(userId, issued.sessionId())).contains(issued);
    }
  }

  @Nested
  class IssueExclusiveDefaultMethod {

    @Test
    void 성공_기존_세션을_전부_회수하고_새_세션만_발급한다() {
      // given
      UUID userId = newUserId();
      UserSession old = registry.issue(userId, NOW);

      // when
      UserSession fresh = registry.issueExclusive(userId, NOW.plusSeconds(1));

      // then
      assertThat(registry.findAllByUserId(userId)).containsExactly(fresh);
      assertThat(registry.find(userId, old.sessionId())).isEmpty();
    }
  }

  @Nested
  class RotateDefaultMethod {

    @Test
    void 성공_sessionId는_유지되고_refreshJti만_바뀐다() {
      // given
      UUID userId = newUserId();
      UserSession issued = registry.issue(userId, NOW);

      // when
      UserSession rotated = registry.rotate(userId, issued.sessionId(),
          issued.currentRefreshJti(), NOW.plusSeconds(1));

      // then
      assertThat(rotated.sessionId()).isEqualTo(issued.sessionId());
      assertThat(rotated.currentRefreshJti()).isNotEqualTo(issued.currentRefreshJti());
    }

    @Test
    void 실패_존재하지_않는_세션이면_UserSessionExpiredException이_발생한다() {
      // given
      UUID userId = newUserId();

      // when & then
      assertThatThrownBy(() -> registry.rotate(userId, UUID.randomUUID(), UUID.randomUUID(), NOW))
          .isInstanceOf(UserSessionExpiredException.class);
    }

    @Test
    void 실패_refreshJti가_다르면_RefreshTokenReusedException이_발생한다() {
      // given
      UUID userId = newUserId();
      UserSession issued = registry.issue(userId, NOW);

      // when & then
      assertThatThrownBy(() -> registry.rotate(userId, issued.sessionId(),
          UUID.randomUUID(), NOW.plusSeconds(1)))
          .isInstanceOf(RefreshTokenReusedException.class);
    }
  }

  @Nested
  class VerifyUserSessionDefaultMethod {

    @Test
    void 성공_존재하면_세션을_반환한다() {
      // given
      UUID userId = newUserId();
      UserSession issued = registry.issue(userId, NOW);

      // when
      UserSession found = registry.verifyUserSession(userId, issued.sessionId());

      // then
      assertThat(found).isEqualTo(issued);
    }

    @Test
    void 실패_존재하지_않으면_UserSessionExpiredException이_발생한다() {
      // given
      UUID userId = newUserId();

      // when & then
      assertThatThrownBy(() -> registry.verifyUserSession(userId, UUID.randomUUID()))
          .isInstanceOf(UserSessionExpiredException.class);
    }
  }
}
