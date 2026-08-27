package com.sprint.mission.otboo.domain.weathernotification.sse.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data,
                         Instant createdAt, Long seq) {

  public SseMessage {
    id = (id != null) ? id : UUID.randomUUID();
    receiverIds = Set.copyOf(receiverIds);
    Objects.requireNonNull(createdAt, "createdAt은 필수입니다."); // 암묵적 Instant.now() 기본값 제거 —
    // 호출부가 항상 명시적으로 넘기도록 강제해 운영 경로가 주입된 Clock을 빠뜨리지 않게 한다.
  }

  public SseMessage(UUID id, Set<UUID> receiverIds, String eventName, Object data,
      Instant createdAt) {
    this(id, receiverIds, eventName, data, createdAt, null);
  }

  public SseMessage(Set<UUID> receiverIds, String eventName, Object data, Instant createdAt) {
    this(null, receiverIds, eventName, data, createdAt, null);
  }

  public SseMessage withSeq(long seq) {
    return new SseMessage(id, receiverIds, eventName, data, createdAt, seq);
  }

  public boolean isTargetedTo(UUID userId) {
    return receiverIds.contains(userId);
  }
}