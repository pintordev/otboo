package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

  private static final String INDEX_KEY = "sse:message-index";
  private static final String MESSAGE_KEY_PREFIX = "sse:message:";

  private final StringRedisTemplate redisTemplate;
  private final ZSetOperations<String, String> zSetOps;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration retention;

  public SseMessageRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
      Clock clock, SseReplayBufferProperties replayBufferProperties) {
    this.redisTemplate = redisTemplate;
    this.zSetOps = redisTemplate.opsForZSet();
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.retention = Duration.ofMinutes(replayBufferProperties.retentionMinutes());
  }

  public UUID save(SseMessage message) {
    String messageKey = MESSAGE_KEY_PREFIX + message.id();
    String json = writeJson(message);

    redisTemplate.execute(new SessionCallback<Object>() {
      @Override
      public Object execute(RedisOperations operations) {
        operations.multi();
        operations.opsForValue().set(messageKey, json, retention);
        operations.opsForZSet().add(INDEX_KEY, message.id().toString(),
            message.createdAt().toEpochMilli());
        return operations.exec();
      }
    });
    return message.id();
  }

  public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
    if (lastEventId == null) {
      return List.of();
    }
    Long rank = zSetOps.rank(INDEX_KEY, lastEventId.toString());
    if (rank == null) {
      return List.of();
    }
    Set<String> ids = zSetOps.range(INDEX_KEY, rank + 1, -1);
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<String> jsons = redisTemplate.opsForValue()
        .multiGet(ids.stream().map(id -> MESSAGE_KEY_PREFIX + id).toList());
    return jsons.stream()
        .filter(Objects::nonNull)
        .map(this::readJson)
        .filter(message -> message.isTargetedTo(userId))
        .toList();
  }

  public Instant getLatestCreatedAt() {
    Set<ZSetOperations.TypedTuple<String>> latest = zSetOps.reverseRangeWithScores(INDEX_KEY, 0, 0);
    if (latest == null || latest.isEmpty()) {
      return null;
    }
    Double score = latest.iterator().next().getScore();
    return score != null ? Instant.ofEpochMilli(score.longValue()) : null;
  }

  private String writeJson(SseMessage message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SseMessage 직렬화 실패", e);
    }
  }

  private SseMessage readJson(String json) {
    try {
      return objectMapper.readValue(json, SseMessage.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SseMessage 역직렬화 실패", e);
    }
  }
}