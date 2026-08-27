package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Repository
public class SseMessageRepository {

  // 정렬/재생용 인덱스는 유저별로 분리한다(sse:message-index:{userId}) — 전역 인덱스 하나면
  // 동시 접속자 수에 비례해 재생 상한(maxReplaySize)이 무효화된다.
  private static final String INDEX_KEY_PREFIX = "sse:message-index:";
  private static final String TIME_INDEX_KEY = "sse:message-index-by-time"; // 보관 기간 정리 전용 — score는 createdAt(micros)
  // 만료된 id가 어느 유저 인덱스들에 들어있었는지 evictExpired()가 알아내기 위한 역참조.
  // 메시지 값 키와 동일한 TTL로 관리 — 유저별 인덱스를 정리 안 하고 방치하면 유저 활동량에
  // 비례해 Redis 메모리가 무한 증식하므로 반드시 능동적으로 정리해야 한다.
  private static final String RECEIVERS_KEY_PREFIX = "sse:message-receivers:";
  private static final String SEQ_KEY = "sse:seq";
  private static final String MESSAGE_KEY_PREFIX = "sse:message:";
  private static final RedisScript<List> FIND_REPLAY_IDS_SCRIPT = RedisScript.of(
      new ClassPathResource("scripts/find-replay-ids.lua"), List.class);

  private final StringRedisTemplate redisTemplate;
  private final ZSetOperations<String, String> zSetOps;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration retention;
  private final int maxReplaySize;

  public SseMessageRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
      Clock clock, SseReplayBufferProperties replayBufferProperties) {
    this.redisTemplate = redisTemplate;
    this.zSetOps = redisTemplate.opsForZSet();
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.retention = Duration.ofMinutes(replayBufferProperties.retentionMinutes());
    this.maxReplaySize = replayBufferProperties.maxSize();
  }

  private static String indexKeyFor(UUID userId) {
    return INDEX_KEY_PREFIX + userId;
  }

  private static String receiversKeyFor(UUID messageId) {
    return RECEIVERS_KEY_PREFIX + messageId;
  }

  public long save(SseMessage message) {
    evictExpired();
    long seq = redisTemplate.opsForValue().increment(SEQ_KEY);
    SseMessage withSeq = message.withSeq(seq);
    String messageKey = MESSAGE_KEY_PREFIX + withSeq.id();
    String receiversKey = receiversKeyFor(withSeq.id());
    String json = objectMapper.writeValueAsString(withSeq);

    boolean hasReceivers = !withSeq.receiverIds().isEmpty();
    int expectedResultCount = 1 // SET
        + (hasReceivers ? 2 : 0) // SADD + EXPIRE
        + withSeq.receiverIds().size() // 수신자별 ZADD
        + 1; // TIME_INDEX_KEY ZADD

    List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
      @Override
      public List<Object> execute(RedisOperations operations) {
        operations.multi();
        try {
          operations.opsForValue().set(messageKey, json, retention);
          if (hasReceivers) { // SADD는 멤버 0개로 호출할 수 없음
            operations.opsForSet().add(receiversKey,
                withSeq.receiverIds().stream().map(UUID::toString).toArray(String[]::new));
            operations.expire(receiversKey, retention);
          }
          withSeq.receiverIds().forEach(receiverId ->
              operations.opsForZSet().add(indexKeyFor(receiverId), withSeq.id().toString(), seq));
          operations.opsForZSet().add(TIME_INDEX_KEY, withSeq.id().toString(),
              toEpochMicros(withSeq.createdAt()));
          return operations.exec();
        } catch (RuntimeException e) {
          // multi() 이후 예외가 나면 discard()로 연결의 트랜잭션 상태를 정리하지 않으면, 이후 이
          // 연결을 재사용하는 명령이 의도치 않게 트랜잭션 큐에 들어가거나 Redis 오류를 낼 수 있다.
          operations.discard();
          throw e;
        }
      }
    });
    if (results == null || results.size() != expectedResultCount) {
      log.warn("SseMessage 저장 MULTI/EXEC 일부 실패 가능성: messageId={}, results={}",
          withSeq.id(), results);
    }
    return seq;
  }

  public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
    evictExpired();
    if (lastEventId == null) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<String> ids = redisTemplate.execute(FIND_REPLAY_IDS_SCRIPT,
        List.of(indexKeyFor(userId)), lastEventId.toString(), String.valueOf(maxReplaySize));
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<String> jsons = redisTemplate.opsForValue()
        .multiGet(ids.stream().map(id -> MESSAGE_KEY_PREFIX + id).toList());
    return jsons.stream()
        .filter(Objects::nonNull)
        .map(this::readJsonSafely)
        .filter(Objects::nonNull)
        .toList(); // isTargetedTo 필터 불필요 - 인덱스 자체가 이미 유저별
  }

  private SseMessage readJsonSafely(String json) {
    try {
      return objectMapper.readValue(json, SseMessage.class);
    } catch (JacksonException e) {
      log.warn("SseMessage 역직렬화 실패, 해당 레코드는 건너뛴다", e);
      return null;
    }
  }

  // sse:seq 카운터 자체가 이미 전역 단조 시퀀스이므로, 이 값을 그대로 읽으면 인덱스를 유저별로
  // 쪼갠 뒤에도(7번) 전역 최신 seq를 별도 자료구조 없이 구할 수 있다.
  public Long getLatestSequence() {
    evictExpired();
    String value = redisTemplate.opsForValue().get(SEQ_KEY);
    return value != null ? Long.valueOf(value) : null;
  }

  // 만료 판정은 TIME_INDEX_KEY(createdAt 기준)로 한다. 만료된 id마다 RECEIVERS_KEY로 그 id가
  // 들어있던 유저 인덱스들을 역추적해 함께 제거한다 — 유저별 인덱스를 정리하지 않고 방치하면
  // 유저 활동량에 비례해 Redis 메모리가 무한 증식한다.
  private void evictExpired() {
    Instant threshold = Instant.now(clock).minus(retention);
    Set<String> expiredIds = zSetOps.rangeByScore(TIME_INDEX_KEY, 0, toEpochMicros(threshold));
    if (expiredIds == null || expiredIds.isEmpty()) {
      return;
    }
    for (String id : expiredIds) {
      String receiversKey = receiversKeyFor(UUID.fromString(id));
      Set<String> receiverIds = redisTemplate.opsForSet().members(receiversKey);
      if (receiverIds != null) {
        receiverIds.forEach(receiverId ->
            zSetOps.remove(indexKeyFor(UUID.fromString(receiverId)), id));
      }
      redisTemplate.delete(receiversKey);
    }
    zSetOps.remove(TIME_INDEX_KEY, expiredIds.toArray());
  }

  private static double toEpochMicros(Instant instant) {
    return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
  }
}