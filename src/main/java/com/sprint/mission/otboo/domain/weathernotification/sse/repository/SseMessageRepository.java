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

  private static final String INDEX_KEY = "sse:message-index";
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

  public UUID save(SseMessage message) {
    evictExpired();
    String messageKey = MESSAGE_KEY_PREFIX + message.id();
    String json = objectMapper.writeValueAsString(message);

    redisTemplate.execute(new SessionCallback<Object>() {
      @Override
      public Object execute(RedisOperations operations) {
        operations.multi();
        operations.opsForValue().set(messageKey, json, retention);
        operations.opsForZSet().add(INDEX_KEY, message.id().toString(),
            toEpochMicros(message.createdAt()));
        return operations.exec();
      }
    });
    return message.id();
  }

  public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
    evictExpired();
    if (lastEventId == null) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<String> ids = redisTemplate.execute(FIND_REPLAY_IDS_SCRIPT, List.of(INDEX_KEY),
        lastEventId.toString(), String.valueOf(maxReplaySize));
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<String> jsons = redisTemplate.opsForValue()
        .multiGet(ids.stream().map(id -> MESSAGE_KEY_PREFIX + id).toList());
    return jsons.stream()
        .filter(Objects::nonNull)
        .map(this::readJsonSafely)
        .filter(Objects::nonNull)
        .filter(message -> message.isTargetedTo(userId))
        .toList();
  }

  private SseMessage readJsonSafely(String json) {
    try {
      return objectMapper.readValue(json, SseMessage.class);
    } catch (JacksonException e) {
      log.warn("SseMessage 역직렬화 실패, 해당 레코드는 건너뛴다", e);
      return null;
    }
  }

  public Instant getLatestCreatedAt() {
    evictExpired();
    Set<ZSetOperations.TypedTuple<String>> latest = zSetOps.reverseRangeWithScores(INDEX_KEY, 0, 0);
    if (latest == null || latest.isEmpty()) {
      return null;
    }
    Double score = latest.iterator().next().getScore();
    return score != null ? fromEpochMicros(score.longValue()) : null;
  }

  private void evictExpired() {
    Instant threshold = Instant.now(clock).minus(retention);
    zSetOps.removeRangeByScore(INDEX_KEY, 0, toEpochMicros(threshold));
  }

  private static double toEpochMicros(Instant instant) {
    return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
  }

  private static Instant fromEpochMicros(long micros) {
    long seconds = Math.floorDiv(micros, 1_000_000L);
    long nanos = Math.floorMod(micros, 1_000_000L) * 1_000L;
    return Instant.ofEpochSecond(seconds, nanos);
  }
}