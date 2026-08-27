package com.sprint.mission.otboo.domain.weathernotification.sse.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data,
                         Instant createdAt, Long seq) {

  public SseMessage {
    id = (id != null) ? id : UUID.randomUUID();
    receiverIds = Set.copyOf(receiverIds);
    createdAt = (createdAt != null) ? createdAt : Instant.now();
  }

  public SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data,
      Instant createdAt) {
    this(id, receiverIds, eventName, data, createdAt, null);
  }

  public SseMessage(Set<UUID> receiverIds, String eventName, Object data, Instant createdAt) {
    this(null, receiverIds, eventName, data, createdAt, null);
  }

  public SseMessage(Set<UUID> receiverIds, String eventName, Object data) {
    this(null, receiverIds, eventName, data, null, null);
  }

  public SseMessage withSeq(long seq) {
    return new SseMessage(id, receiverIds, eventName, data, createdAt, seq);
  }

  public boolean isTargetedTo(UUID userId) {
    return receiverIds.contains(userId);
  }
}