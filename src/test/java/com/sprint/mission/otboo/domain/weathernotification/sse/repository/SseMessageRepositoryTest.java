package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.ObjectMapper;

class SseMessageRepositoryTest implements RedisTestContainerSupport {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  private final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true) // createdAt이 null이면 SseMessage 생성 자체가 NPE라 반드시 필요
      .build();

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private SseMessageRepository sseMessageRepository;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

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
    deleteByPattern("sse:message:*");
    deleteByPattern("sse:message-receivers:*");
    deleteByPattern("sse:message-index:*");
    redisTemplate.delete("sse:message-index-by-time");
    redisTemplate.delete("sse:seq");

    sseMessageRepository = new SseMessageRepository(redisTemplate,
        new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
        new SseReplayBufferProperties(10, 1000));

    logger = (Logger) LoggerFactory.getLogger(SseMessageRepository.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  private void deleteByPattern(String pattern) {
    Set<String> keys = redisTemplate.keys(pattern);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Nested
  @DisplayName("메시지 저장")
  class Save {

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
    @DisplayName("저장한 메시지의 id가 수신자별 인덱스에 seq를 score로 등록된다")
    void 저장한_메시지의_id가_인덱스에_등록된다() {
      // given
      UUID receiverId = UUID.randomUUID();
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("receiverIds", Set.of(receiverId))
          .set("data", "payload")
          .sample();

      // when
      long seq = sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForZSet()
          .score("sse:message-index:" + receiverId, message.id().toString()))
          .isEqualTo((double) seq);
    }

    @Test
    @DisplayName("서로_다른_인스턴스에서_저장해도_seq는_전역적으로_단조_증가한다")
    void 서로_다른_인스턴스에서_저장해도_seq는_전역적으로_단조_증가한다() {
      // given - 두 번째 저장의 createdAt이 첫 번째보다 이르다(시계 스큐 재현)
      SseMessage earlierClockLaterSave = fm.giveMeBuilder(SseMessage.class)
          .set("data", "payload")
          .set("createdAt", NOW.minusSeconds(1)) // 다른 인스턴스 시계가 1초 느림
          .sample();
      SseMessage laterClockEarlierSave = fm.giveMeBuilder(SseMessage.class)
          .set("data", "payload")
          .set("createdAt", NOW)
          .sample();

      // when
      long firstSeq = sseMessageRepository.save(laterClockEarlierSave);
      long secondSeq = sseMessageRepository.save(earlierClockLaterSave);

      // then - createdAt은 두 번째가 더 이르지만, 저장 순서(seq)는 항상 증가해야 한다
      assertThat(secondSeq).isGreaterThan(firstSeq);
    }
  }

  @Nested
  @DisplayName("MULTI/EXEC 결과 검사")
  class MultiExecResultCheck {

    @Test
    @DisplayName("정상_저장이면_경고_로그를_남기지_않는다")
    void 정상_저장이면_경고_로그를_남기지_않는다() {
      // given
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("data", "payload")
          .sample();

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(msg -> msg.contains("MULTI/EXEC 일부 실패"));
    }

    @Test
    @DisplayName("MULTI_EXEC_결과_개수가_예상과_다르면_경고_로그를_남긴다")
    void MULTI_EXEC_결과_개수가_예상과_다르면_경고_로그를_남긴다() {
      // given - StringRedisTemplate을 목으로 대체해 exec() 결과 개수를 인위적으로 어긋나게 만든다
      // (Lettuce 파이프라인 특성상 실제 부분 실패를 통합 테스트로 재현하긴 어려움)
      StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      @SuppressWarnings("unchecked")
      ZSetOperations<String, String> zSetOpsMock = mock(ZSetOperations.class);
      given(mockTemplate.opsForValue()).willReturn(valueOps);
      given(mockTemplate.opsForZSet()).willReturn(zSetOpsMock);
      given(valueOps.increment("sse:seq")).willReturn(1L);
      given(zSetOpsMock.rangeByScore(anyString(), anyDouble(), anyDouble()))
          .willReturn(Set.of());
      given(mockTemplate.execute(any(SessionCallback.class)))
          .willReturn(List.of("OK")); // 수신자 1명이면 기대 개수는 5(SET+SADD+EXPIRE+ZADD+ZADD)

      SseMessageRepository repository = new SseMessageRepository(mockTemplate,
          new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
          new SseReplayBufferProperties(10, 1000));
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("data", "payload")
          .sample();

      // when
      repository.save(message);

      // then
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(msg -> msg.contains("MULTI/EXEC 일부 실패"));
    }

    @Test
    @DisplayName("MULTI_이후_예외가_발생하면_discard_후_원래_예외를_재전파한다")
    void MULTI_이후_예외가_발생하면_discard_후_원래_예외를_재전파한다() {
      // given - execute(SessionCallback)을 직접 흉내내 RedisOperations를 목으로 넘긴다
      // (Lettuce 파이프라인 특성상 실제 커넥션 장애를 통합 테스트로 재현하긴 어려움)
      StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      @SuppressWarnings("unchecked")
      ZSetOperations<String, String> zSetOpsMock = mock(ZSetOperations.class);
      given(mockTemplate.opsForValue()).willReturn(valueOps);
      given(mockTemplate.opsForZSet()).willReturn(zSetOpsMock);
      given(valueOps.increment("sse:seq")).willReturn(1L);
      given(zSetOpsMock.rangeByScore(anyString(), anyDouble(), anyDouble()))
          .willReturn(Set.of());

      @SuppressWarnings("unchecked")
      RedisOperations<String, String> mockOperations = mock(RedisOperations.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> opsValueOps = mock(ValueOperations.class);
      @SuppressWarnings("unchecked")
      SetOperations<String, String> opsSetOps = mock(SetOperations.class);
      @SuppressWarnings("unchecked")
      ZSetOperations<String, String> opsZSetOps = mock(ZSetOperations.class);
      given(mockOperations.opsForValue()).willReturn(opsValueOps);
      given(mockOperations.opsForSet()).willReturn(opsSetOps);
      given(mockOperations.opsForZSet()).willReturn(opsZSetOps);
      RuntimeException redisFailure = new RuntimeException("Redis 장애");
      given(opsZSetOps.add(anyString(), anyString(), anyDouble())).willThrow(redisFailure);
      given(mockTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
        SessionCallback<?> callback = invocation.getArgument(0);
        return callback.execute(mockOperations);
      });

      SseMessageRepository repository = new SseMessageRepository(mockTemplate,
          new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
          new SseReplayBufferProperties(10, 1000));
      SseMessage message = fm.giveMeBuilder(SseMessage.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("data", "payload")
          .sample();

      // when & then
      assertThatThrownBy(() -> repository.save(message)).isSameAs(redisFailure);
      verify(mockOperations).discard();
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
      forUser = forUser.withSeq(sseMessageRepository.save(forUser));

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
      valid = valid.withSeq(sseMessageRepository.save(valid));

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(anchor.id(), userId);

      // then
      assertThat(found).containsExactly(valid);
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
      m3 = m3.withSeq(cappedRepository.save(m3));
      m4 = m4.withSeq(cappedRepository.save(m4));
      m5 = m5.withSeq(cappedRepository.save(m5));

      // when
      List<SseMessage> found = cappedRepository.findAllAfter(anchor.id(), userId);

      // then — anchor 이후 5건 중 상한(3건)을 넘는 가장 오래된 m1, m2는 재생에서 제외된다
      assertThat(found).containsExactly(m3, m4, m5);
    }
  }

  @Nested
  @DisplayName("유저별 인덱스 격리")
  class PerUserIndexIsolation {

    @Test
    @DisplayName("한_유저의_메시지가_많아도_다른_유저의_재생_상한에_영향을_주지_않는다")
    void 한_유저의_메시지가_많아도_다른_유저의_재생_상한에_영향을_주지_않는다() {
      // given - target 메시지 1건을 먼저 저장한 뒤, maxReplaySize보다 많은 타인(other) 메시지로
      // 뒤덮는다. 전역 인덱스였다면 target 메시지가 최신 N건 밖으로 밀려 잘렸어야 한다.
      SseMessageRepository smallCapRepository = new SseMessageRepository(redisTemplate,
          new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), new SseReplayBufferProperties(10, 5));
      UUID other = UUID.randomUUID();
      UUID target = UUID.randomUUID();
      SseMessage anchor = new SseMessage(Set.of(target), "notifications", "anchor", NOW);
      anchor = anchor.withSeq(smallCapRepository.save(anchor));
      SseMessage targetMessage = new SseMessage(Set.of(target), "notifications", "for-target", NOW);
      long targetSeq = smallCapRepository.save(targetMessage);
      for (int i = 0; i < 10; i++) { // other 메시지 10건으로 maxSize(5)를 넘김
        smallCapRepository.save(new SseMessage(Set.of(other), "notifications", "other-" + i, NOW));
      }

      // when
      List<SseMessage> result = smallCapRepository.findAllAfter(anchor.id(), target);

      // then - 전역 인덱스였다면 other 10건에 밀려 target 메시지가 상한 밖으로 잘렸어야 한다
      assertThat(result).extracting(SseMessage::seq).contains(targetSeq);
    }
  }

  @Nested
  @DisplayName("최신 seq 조회")
  class GetLatestSequence {

    @Test
    @DisplayName("메시지가 없으면 null을 반환한다")
    void 메시지가_없으면_null을_반환한다() {
      // when & then
      assertThat(sseMessageRepository.getLatestSequence()).isNull();
    }

    @Test
    @DisplayName("가장 최근 저장한 메시지의 seq를 반환한다")
    void 가장_최근_저장한_메시지의_seq를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant earlier = NOW.minusSeconds(10);
      SseMessage first = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "first", earlier);
      SseMessage second = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "second", NOW);
      sseMessageRepository.save(first);
      long secondSeq = sseMessageRepository.save(second);

      // when & then
      assertThat(sseMessageRepository.getLatestSequence()).isEqualTo(secondSeq);
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
      kept = kept.withSeq(sseMessageRepository.save(kept));

      // when
      List<SseMessage> found = sseMessageRepository.findAllAfter(anchor.id(), userId);

      // then
      assertThat(found).containsExactly(kept);
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index-by-time", expired.id().toString()))
          .isNull();
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index:" + userId, expired.id().toString()))
          .isNull();
      assertThat(redisTemplate.hasKey("sse:message-receivers:" + expired.id())).isFalse();
    }

    @Test
    @DisplayName("findAllAfter/getLatestSequence 없이 save만 반복해도 보관 기간이 지난 항목이 정리된다")
    void save만_반복해도_보관_기간이_지난_항목이_정리된다() {
      // given
      UUID userId = UUID.randomUUID();
      SseMessage expired = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "old", NOW.minus(Duration.ofMinutes(11)));
      sseMessageRepository.save(expired);

      // when — 조회 메서드를 거치지 않고 save만 한 번 더 호출
      sseMessageRepository.save(new SseMessage(Set.of(userId), "notifications", "fresh", NOW));

      // then
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index-by-time", expired.id().toString()))
          .isNull();
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index:" + userId, expired.id().toString()))
          .isNull();
    }

    @Test
    @DisplayName("한_메시지가_여러_수신자에게_갔어도_만료_시_모든_수신자_인덱스에서_제거된다")
    void 한_메시지가_여러_수신자에게_갔어도_만료_시_모든_수신자_인덱스에서_제거된다() {
      // given — 팬아웃 메시지(수신자 여러 명)
      UUID receiver1 = UUID.randomUUID();
      UUID receiver2 = UUID.randomUUID();
      SseMessage expired = new SseMessage(UUID.randomUUID(), Set.of(receiver1, receiver2),
          "notifications", "old", NOW.minus(Duration.ofMinutes(11)));
      sseMessageRepository.save(expired);

      // when — 조회 메서드를 거치지 않고 save만 한 번 더 호출해 evictExpired()를 태운다
      sseMessageRepository.save(
          new SseMessage(Set.of(receiver1), "notifications", "fresh", NOW));

      // then — 두 수신자 인덱스에서 모두 제거된다
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index:" + receiver1, expired.id().toString()))
          .isNull();
      assertThat(redisTemplate.opsForZSet()
          .rank("sse:message-index:" + receiver2, expired.id().toString()))
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
                new SseMessage(Set.of(userId), "notifications", "payload-" + index, NOW));
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