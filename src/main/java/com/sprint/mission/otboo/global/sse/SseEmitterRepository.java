package com.sprint.mission.otboo.global.sse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

  private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  public SseEmitter save(UUID userId, SseEmitter emitter) {
    emitters.put(userId, emitter);
    return emitter;
  }

  public SseEmitter findByUserId(UUID userId) {
    return emitters.get(userId);
  }

  public void deleteByUserId(UUID userId) {
    emitters.remove(userId);
  }
}