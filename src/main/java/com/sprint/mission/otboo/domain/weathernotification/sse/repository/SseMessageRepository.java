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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

  private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
  private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Clock clock;
  private final Duration retention;

  public SseMessageRepository(Clock clock, SseReplayBufferProperties replayBufferProperties) {
    this.clock = clock;
    this.retention = Duration.ofMinutes(replayBufferProperties.retentionMinutes());
  }

  public UUID save(SseMessage message) {
    lock.writeLock().lock();
    try {
      messages.put(message.id(), message);
      eventIdQueue.addLast(message.id());
      evictExpired();
      return message.id();
    } finally {
      lock.writeLock().unlock();
    }
  }

  public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
    lock.writeLock().lock();
    try {
      evictExpired();
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
    } finally {
      lock.writeLock().unlock();
    }
  }

  public Instant getLatestCreatedAt() {
    lock.writeLock().lock();
    try {
      evictExpired();
      UUID latestId = eventIdQueue.peekLast();
      if (latestId == null) {
        return null;
      }
      SseMessage latest = messages.get(latestId);
      return latest != null ? latest.createdAt() : null;
    } finally {
      lock.writeLock().unlock();
    }
  }

  // save()/findAllAfter()/getLatestCreatedAt()의 writeLock 안에서만 호출된다 —
  // 별도 락 없이 eventIdQueue/messages를 직접 조작. 유휴 상태(추가 save 없이 조회만 반복)에서도
  // 보관 기간이 지난 메시지가 남아있지 않도록 조회 메서드도 readLock이 아닌 writeLock을 잡고 호출한다.
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
