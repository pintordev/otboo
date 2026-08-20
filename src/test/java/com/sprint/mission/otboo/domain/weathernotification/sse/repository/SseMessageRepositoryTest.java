package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class SseMessageRepositoryTest implements RedisTestContainerSupport {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private SseMessageRepository sseMessageRepository;

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
    Set<String> messageKeys = redisTemplate.keys("sse:message:*");
    if (messageKeys != null && !messageKeys.isEmpty()) {
      redisTemplate.delete(messageKeys);
    }
    redisTemplate.delete("sse:message-index");

    sseMessageRepository = new SseMessageRepository(redisTemplate,
        new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
        new SseReplayBufferProperties(10));
  }

  @Nested
  @DisplayName("메시지 저장")
  class Save {

    @Test
    @DisplayName("저장하면 메시지의 id를 반환한다")
    void 저장하면_메시지의_id를_반환한다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when & then
      assertThat(sseMessageRepository.save(message)).isEqualTo(message.id());
    }

    @Test
    @DisplayName("저장한 메시지는 Redis에 JSON으로 기록된다")
    void 저장한_메시지는_Redis에_JSON으로_기록된다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForValue().get("sse:message:" + message.id())).isNotNull();
    }

    @Test
    @DisplayName("저장한 메시지의 id가 인덱스에 생성 시각 score로 등록된다")
    void 저장한_메시지의_id가_인덱스에_등록된다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForZSet().score("sse:message-index", message.id().toString()))
          .isEqualTo((double) message.createdAt().toEpochMilli());
    }
  }

  @Nested
  @DisplayName("이후 메시지 조회")
  class FindAllAfter {

    @Test
    @DisplayName("lastEventId 이후 저장된 메시지 중 수신 대상인 것만 반환한다")
    void lastEventId_이후_저장된_메시지_중_수신_대상인_것만_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      SseMessage anchor = new SseMessage(Set.of(userId), "notifications", "anchor");
      sseMessageRepository.save(anchor);
      SseMessage forOther = new SseMessage(Set.of(otherId), "notifications", "for-other");
      sseMessageRepository.save(forOther);
      SseMessage forUser = new SseMessage(Set.of(userId), "notifications", "for-user");
      sseMessageRepository.save(forUser);

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(anchor.id(), userId);

      // then
      assertThat(found).containsExactly(forUser);
    }

    @Test
    @DisplayName("lastEventId가 null이면 빈 리스트를 반환한다")
    void lastEventId가_null이면_빈_리스트를_반환한다() {
      // when & then
      assertThat(sseMessageRepository.findAllAfter(null, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("lastEventId가 존재하지 않으면 빈 리스트를 반환한다")
    void lastEventId가_존재하지_않으면_빈_리스트를_반환한다() {
      // when & then
      assertThat(sseMessageRepository.findAllAfter(UUID.randomUUID(), UUID.randomUUID()))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("최신 생성 시각 조회")
  class GetLatestCreatedAt {

    @Test
    @DisplayName("메시지가 없으면 null을 반환한다")
    void 메시지가_없으면_null을_반환한다() {
      // when & then
      assertThat(sseMessageRepository.getLatestCreatedAt()).isNull();
    }

    @Test
    @DisplayName("가장 최근 저장한 메시지의 생성 시각을 반환한다")
    void 가장_최근_저장한_메시지의_생성_시각을_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant earlier = NOW.minusSeconds(10);
      SseMessage first = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "first", earlier);
      SseMessage second = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "second", NOW);
      sseMessageRepository.save(first);
      sseMessageRepository.save(second);

      // when & then
      assertThat(sseMessageRepository.getLatestCreatedAt()).isEqualTo(NOW);
    }
  }

  @Nested
  @DisplayName("보관 기간 초과 시 정리")
  class Eviction {

    @Test
    @DisplayName("보관 기간이 지난 메시지는 조회 시점에 인덱스에서 제거된다")
    void 보관_기간이_지난_메시지는_조회_시점에_인덱스에서_제거된다() {
      // given — retention 10분, NOW를 "지금"으로 고정해둔 저장소를 그대로 쓴다
      UUID userId = UUID.randomUUID();
      SseMessage expired = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "old", NOW.minus(Duration.ofMinutes(11)));
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", NOW.minus(Duration.ofMinutes(6)));
      SseMessage kept = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "kept", NOW.minus(Duration.ofMinutes(1)));
      sseMessageRepository.save(expired);
      sseMessageRepository.save(anchor);
      sseMessageRepository.save(kept);

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(anchor.id(), userId);

      // then
      assertThat(found).containsExactly(kept);
      assertThat(redisTemplate.opsForZSet().rank("sse:message-index", expired.id().toString()))
          .isNull();
    }
  }

  @Nested
  @DisplayName("동시성")
  class Concurrency {

    @Test
    @DisplayName("save가 동시에 들어와도 MULTI/EXEC 원자성으로 유실 없이 전부 조회된다")
    void save가_동시에_들어와도_유실_없이_전부_조회된다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      int concurrency = 20;
      SseMessage anchor = new SseMessage(Set.of(userId), "notifications", "anchor");
      sseMessageRepository.save(anchor);

      ExecutorService executor = Executors.newFixedThreadPool(concurrency);
      CountDownLatch ready = new CountDownLatch(concurrency);
      CountDownLatch start = new CountDownLatch(1);
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < concurrency; i++) {
        int index = i;
        tasks.add(() -> {
          ready.countDown();
          start.await();
          sseMessageRepository.save(
              new SseMessage(Set.of(userId), "notifications", "payload-" + index));
          return null;
        });
      }

      // when
      List<Future<Void>> futures = new ArrayList<>();
      for (Callable<Void> task : tasks) {
        futures.add(executor.submit(task));
      }
      ready.await();
      start.countDown();
      for (Future<Void> future : futures) {
        future.get();
      }
      executor.shutdown();

      // then
      assertThat(sseMessageRepository.findAllAfter(anchor.id(), userId)).hasSize(concurrency);
    }
  }
}