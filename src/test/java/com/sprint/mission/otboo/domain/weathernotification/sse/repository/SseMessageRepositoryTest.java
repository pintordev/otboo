package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
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

  private final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

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
        new SseReplayBufferProperties(10, 1000));
  }

  @Nested
  @DisplayName("메시지 저장")
  class Save {

    @Test
    @DisplayName("저장하면 메시지의 id를 반환한다")
    void 저장하면_메시지의_id를_반환한다() {
      // given
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("data", "payload")
          .sample();

      // when & then
      assertThat(sseMessageRepository.save(message)).isEqualTo(message.id());
    }

    @Test
    @DisplayName("저장한 메시지는 Redis에 JSON으로 기록된다")
    void 저장한_메시지는_Redis에_JSON으로_기록된다() {
      // given
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("data", "payload")
          .sample();

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForValue().get("sse:message:" + message.id())).isNotNull();
    }

    @Test
    @DisplayName("저장한 메시지의 id가 인덱스에 생성 시각 score로 등록된다")
    void 저장한_메시지의_id가_인덱스에_등록된다() {
      // given
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("data", "payload")
          .sample();

      // when
      sseMessageRepository.save(message);

      // then
      Instant createdAt = message.createdAt();
      double expectedMicros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1_000L;
      assertThat(redisTemplate.opsForZSet().score("sse:message-index", message.id().toString()))
          .isEqualTo(expectedMicros);
    }
  }

  @Nested
  @DisplayName("이후 메시지 조회")
  class FindAllAfter {

    @Test
    @DisplayName("lastEventId 이후 저장된 메시지 중 수신 대상인 것만 반환한다")
    void lastEventId_이후_저장된_메시지_중_수신_대상인_것만_반환한다() {
      // given — createdAt을 명시적으로 서로 다르게 고정해 정렬 순서가 실행 타이밍에 좌우되지 않게 한다
      UUID userId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", NOW.minusSeconds(2));
      sseMessageRepository.save(anchor);
      SseMessage forOther = new SseMessage(UUID.randomUUID(), Set.of(otherId), "notifications",
          "for-other", NOW.minusSeconds(1));
      sseMessageRepository.save(forOther);
      SseMessage forUser = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "for-user", NOW);
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

    @Test
    @DisplayName("역직렬화에 실패한 레코드는 건너뛰고 나머지는 정상 반환한다")
    void 역직렬화에_실패한_레코드는_건너뛰고_나머지는_정상_반환한다() {
      // given — corrupted의 String 레코드만 직접 손상시킨다(인덱스 항목은 그대로 남아있음)
      UUID userId = UUID.randomUUID();
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", NOW.minusSeconds(2));
      sseMessageRepository.save(anchor);
      SseMessage corrupted = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "corrupted", NOW.minusSeconds(1));
      sseMessageRepository.save(corrupted);
      redisTemplate.opsForValue().set("sse:message:" + corrupted.id(), "not-valid-json");
      SseMessage valid = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "valid", NOW);
      sseMessageRepository.save(valid);

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(anchor.id(), userId);

      // then
      assertThat(found).containsExactly(valid);
    }

    @Test
    @DisplayName("같은 밀리초 안에서도 마이크로초 정밀도로 실제 생성 순서를 따른다")
    void 같은_밀리초_안에서도_마이크로초_정밀도로_실제_생성_순서를_따른다() {
      // given — m1이 m2보다 먼저 생성됐지만(같은 밀리초, 마이크로초만 다름) m1의 id가
      // m2의 id보다 사전순으로 크다 — 밀리초 단위 score라면 둘이 동점이라 사전순(m2, m1)으로
      // tie-break되어, lastEventId=m1일 때 ZRANK가 m1을 m2보다 뒤로 잡아 m2가 재생에서 빠진다
      UUID userId = UUID.randomUUID();
      Instant m1CreatedAt = NOW.plusNanos(100_000);
      Instant m2CreatedAt = NOW.plusNanos(900_000);
      SseMessage m1 = new SseMessage(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
          Set.of(userId), "notifications", "m1", m1CreatedAt);
      SseMessage m2 = new SseMessage(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
          Set.of(userId), "notifications", "m2", m2CreatedAt);
      sseMessageRepository.save(m1);
      sseMessageRepository.save(m2);

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(m1.id(), userId);

      // then — m2가 m1보다 나중에 생성됐으므로 재생 대상에 포함돼야 한다
      assertThat(found).containsExactly(m2);
    }
  }

  @Nested
  @DisplayName("재생 조회 상한")
  class ReplayCap {

    @Test
    @DisplayName("보관 기간 내 메시지가 상한을 넘으면 최신 N건만 재생한다")
    void 보관_기간_내_메시지가_상한을_넘으면_최신_N건만_재생한다() {
      // given — 상한을 3으로 좁힌 저장소를 별도로 구성해 상한 초과 상황을 재현한다
      SseMessageRepository cappedRepository = new SseMessageRepository(redisTemplate,
          new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
          new SseReplayBufferProperties(10, 3));
      UUID userId = UUID.randomUUID();
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", NOW.minusSeconds(10));
      SseMessage m1 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "m1", NOW.minusSeconds(4));
      SseMessage m2 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "m2", NOW.minusSeconds(3));
      SseMessage m3 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "m3", NOW.minusSeconds(2));
      SseMessage m4 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "m4", NOW.minusSeconds(1));
      SseMessage m5 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "m5", NOW);
      cappedRepository.save(anchor);
      cappedRepository.save(m1);
      cappedRepository.save(m2);
      cappedRepository.save(m3);
      cappedRepository.save(m4);
      cappedRepository.save(m5);

      // when
      List<SseMessage> found = cappedRepository.findAllAfter(anchor.id(), userId);

      // then — anchor 이후 5건 중 상한(3건)을 넘는 가장 오래된 m1, m2는 재생에서 제외된다
      assertThat(found).containsExactly(m3, m4, m5);
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

    @Test
    @DisplayName("findAllAfter/getLatestCreatedAt 없이 save만 반복해도 보관 기간이 지난 항목이 정리된다")
    void save만_반복해도_보관_기간이_지난_항목이_정리된다() {
      // given
      UUID userId = UUID.randomUUID();
      SseMessage expired = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "old", NOW.minus(Duration.ofMinutes(11)));
      sseMessageRepository.save(expired);

      // when — 조회 메서드를 거치지 않고 save만 한 번 더 호출
      sseMessageRepository.save(new SseMessage(Set.of(userId), "notifications", "fresh"));

      // then
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
      // given — anchor는 동시 저장되는 메시지들보다 명확히 이른 시각으로 고정한다
      UUID userId = UUID.randomUUID();
      int concurrency = 20;
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", NOW.minusSeconds(1));
      sseMessageRepository.save(anchor);

      ExecutorService executor = Executors.newFixedThreadPool(concurrency);
      try {
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

        // then
        assertThat(sseMessageRepository.findAllAfter(anchor.id(), userId)).hasSize(concurrency);
      } finally {
        executor.shutdown();
      }
    }
  }
}