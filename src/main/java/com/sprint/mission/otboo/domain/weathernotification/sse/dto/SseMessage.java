package com.sprint.mission.otboo.domain.weathernotification.sse.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data,
                         Instant createdAt) {

  public SseMessage {
    id = (id != null) ? id : UUID.randomUUID();
    receiverIds = Set.copyOf(receiverIds);
    createdAt = (createdAt != null) ? createdAt : Instant.now();
  }

  public SseMessage(Set<UUID> receiverIds, String eventName, Object data) {
    this(null, receiverIds, eventName, data, null);
  }

  public boolean isTargetedTo(UUID userId) {
    return receiverIds.contains(userId);
  }
}