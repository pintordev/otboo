package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

  private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
  private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration retention;

  public SseMessageRepository(Clock clock, SseReplayBufferProperties replayBufferProperties) {
    this.clock = clock;
    this.retention = Duration.ofMinutes(replayBufferProperties.retentionMinutes());
  }

  public UUID save(SseMessage message) {
    messages.put(message.id(), message);
    eventIdQueue.addLast(message.id());
    evictExpired();
    return message.id();
  }

  public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
    if (lastEventId == null || !messages.containsKey(lastEventId)) {
      return List.of();
    }
    return eventIdQueue.stream()
        .dropWhile(id -> !id.equals(lastEventId))
        .skip(1)
        .map(messages::get)
        .filter(Objects::nonNull)
        .filter(message -> message.isTargetedTo(userId))
        .toList();
  }

  public Instant getLatestCreatedAt() {
    UUID latestId = eventIdQueue.peekLast();
    if (latestId == null) {
      return null;
    }
    SseMessage latest = messages.get(latestId);
    return latest != null ? latest.createdAt() : null;
  }

  private void evictExpired() {
    Instant threshold = Instant.now(clock).minus(retention);
    UUID oldestId;
    while ((oldestId = eventIdQueue.peekFirst()) != null) {
      SseMessage oldest = messages.get(oldestId);
      if (oldest == null || oldest.createdAt().isBefore(threshold)) {
        eventIdQueue.pollFirst();
        messages.remove(oldestId);
      } else {
        break;
      }
    }
  }
}
