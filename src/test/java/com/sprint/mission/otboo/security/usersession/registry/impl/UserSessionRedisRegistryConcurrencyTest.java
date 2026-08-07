package com.sprint.mission.otboo.security.usersession.registry.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.business.RefreshTokenReusedException;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.policy.impl.SlidingExpirationPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// replace-all-user-sessions.lua / compare-and-rotate.lua가 실제로 레이스를 없애는지,
// 그리고 revokeAll()이 동시 save()와 부딪혀도 좀비 세션을 안 만드는지를 스레드풀로 재현해서 검증한다.
@Testcontainers
class UserSessionRedisRegistryConcurrencyTest {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static final Duration TTL = Duration.ofMinutes(30);
  // Redis EXPIREAT는 Redis 서버의 실제 시스템 시계로 평가되므로 실제 현재 시각에 가까워야 한다.
  private static final Instant NOW = Instant.now();
  private static final int CONCURRENCY = 20;

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private UserSessionRedisRegistry registry;
  private ExecutorService executor;

  @BeforeAll
  static void setUpRedis() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
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
    executor = Executors.newFixedThreadPool(CONCURRENCY);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  private static UserSession newSession(UUID userId, Instant issuedAt) {
    return new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), issuedAt);
  }

  @Test
  void 성공_동시에_여러번_단일기기로그인해도_정확히_하나의_세션만_남고_좀비가_생기지_않는다() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    registry.issue(userId, NOW);

    CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<UserSession>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(() -> {
        ready.countDown();
        start.await();
        return registry.issueExclusive(userId, Instant.now());
      });
    }

    // when
    List<Future<UserSession>> futures = new ArrayList<>();
    for (Callable<UserSession> task : tasks) {
      futures.add(executor.submit(task));
    }
    ready.await();
    start.countDown();

    List<UserSession> issuedSessions = new ArrayList<>();
    for (Future<UserSession> future : futures) {
      issuedSessions.add(future.get());
    }

    // then
    List<UserSession> remaining = registry.findAllByUserId(userId);
    assertThat(remaining).hasSize(1);

    UUID survivorSessionId = remaining.get(0).sessionId();
    for (UserSession issued : issuedSessions) {
      if (!issued.sessionId().equals(survivorSessionId)) {
        // 좀비 검증: 인덱스에서 밀려난 세션은 find()로도 완전히 사라져 있어야 한다.
        assertThat(registry.find(userId, issued.sessionId())).isEmpty();
      }
    }
  }

  @Test
  void 성공_같은_refreshJti로_동시에_rotate하면_정확히_하나만_성공하고_나머지는_재사용_또는_만료로_판정된다()
      throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    UserSession issued = registry.issue(userId, NOW);
    UUID originalJti = issued.currentRefreshJti();
    UUID sessionId = issued.sessionId();

    CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < CONCURRENCY; i++) {
      tasks.add(() -> {
        ready.countDown();
        start.await();
        try {
          registry.rotate(userId, sessionId, originalJti, Instant.now());
          return "SUCCESS";
        } catch (RefreshTokenReusedException e) {
          return "REUSED";
        } catch (UserSessionExpiredException e) {
          return "EXPIRED";
        }
      });
    }

    // when
    List<Future<String>> futures = new ArrayList<>();
    for (Callable<String> task : tasks) {
      futures.add(executor.submit(task));
    }
    ready.await();
    start.countDown();

    long successCount = 0;
    long reusedCount = 0;
    long expiredCount = 0;
    for (Future<String> future : futures) {
      switch (future.get()) {
        case "SUCCESS" -> successCount++;
        case "REUSED" -> reusedCount++;
        case "EXPIRED" -> expiredCount++;
        default -> throw new IllegalStateException("예상치 못한 결과");
      }
    }

    // then
    // Redis는 스크립트 호출을 완전히 직렬화하므로, 20개가 동시에 같은(원본) jti로 rotate를 시도하면
    // 직렬 순서상 1번째만 성공하고, 2번째는 jti가 이미 바뀐 걸 보고 재사용으로 판단해 세션을 삭제한다.
    // 그 이후(3번째~)는 이미 삭제된 세션을 보게 되므로 EXPIRED로 떨어진다.
    assertThat(successCount).isEqualTo(1);
    assertThat(reusedCount).isEqualTo(1);
    assertThat(expiredCount).isEqualTo(CONCURRENCY - 2);
    assertThat(registry.findAllByUserId(userId)).isEmpty();
  }

  @Test
  void 성공_revokeAll과_동시에_save가_들어와도_해시와_인덱스_상태가_항상_일치한다() throws Exception {
    int trials = 30;
    for (int trial = 0; trial < trials; trial++) {
      // given
      UUID userId = UUID.randomUUID();
      registry.save(newSession(userId, NOW), NOW.plus(TTL));
      UserSession fresh = newSession(userId, NOW);

      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);

      Callable<Void> revokeTask = () -> {
        ready.countDown();
        start.await();
        registry.revokeAll(userId);
        return null;
      };
      Callable<Void> saveTask = () -> {
        ready.countDown();
        start.await();
        registry.save(fresh, NOW.plus(TTL));
        return null;
      };

      // when
      Future<Void> revokeFuture = executor.submit(revokeTask);
      Future<Void> saveFuture = executor.submit(saveTask);
      ready.await();
      start.countDown();
      revokeFuture.get();
      saveFuture.get();

      // then: 새로 저장한 세션이 해시로 살아있다면 반드시 인덱스에도 있어야 한다 (좀비 금지)
      boolean existsInHash = registry.find(userId, fresh.sessionId()).isPresent();
      boolean existsInIndex = registry.findAllByUserId(userId).stream()
          .anyMatch(s -> s.sessionId().equals(fresh.sessionId()));

      assertThat(existsInHash)
          .as("trial %d: 해시 존재 여부와 인덱스 등재 여부가 일치해야 한다(좀비 세션 금지)", trial)
          .isEqualTo(existsInIndex);
    }
  }
}
