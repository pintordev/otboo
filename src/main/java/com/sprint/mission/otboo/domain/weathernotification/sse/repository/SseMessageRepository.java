package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

  private static final String INDEX_KEY = "sse:message-index";
  private static final String MESSAGE_KEY_PREFIX = "sse:message:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration retention;

  public SseMessageRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
      Clock clock, SseReplayBufferProperties replayBufferProperties) {
    this.redisTemplate = redisTemplate;
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
    throw new UnsupportedOperationException("findAllAfter 미구현");
  }

  public Instant getLatestCreatedAt() {
    throw new UnsupportedOperationException("getLatestCreatedAt 미구현");
  }

  private String writeJson(SseMessage message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SseMessage 직렬화 실패", e);
    }
  }
}