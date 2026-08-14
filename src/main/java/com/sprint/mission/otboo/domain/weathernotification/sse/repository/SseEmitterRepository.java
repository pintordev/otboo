package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

  private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  public void save(UUID userId, SseEmitter emitter) {
    SseEmitter previous = emitters.put(userId, emitter);
    if (previous != null) {
      previous.complete();
    }
  }

  public Optional<SseEmitter> findByUserId(UUID userId) {
    return Optional.ofNullable(emitters.get(userId));
  }

  public void remove(UUID userId, SseEmitter emitter) {
    emitters.computeIfPresent(userId, (id, current) -> current == emitter ? null : current);
  }

  public Map<UUID, SseEmitter> findAll() {
    return Map.copyOf(emitters);
  }
}