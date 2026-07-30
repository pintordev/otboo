package com.sprint.mission.otboo.global.sse;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data, Instant createdAt) {

    public SseMessage {
        id = UUID.randomUUID();
        receiverIds = Set.copyOf(receiverIds);
        createdAt = Instant.now();
    }

    public SseMessage(Set<UUID> receiverIds, String eventName, Object data) {
        this(null, receiverIds, eventName, data, null);
    }

    public boolean isTargetedTo(UUID userId) {
        return receiverIds.contains(userId);
    }
}