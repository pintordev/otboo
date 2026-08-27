package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.EmitterConnection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

  private final Map<UUID, EmitterConnection> connections = new ConcurrentHashMap<>();

  public Optional<EmitterConnection> save(UUID userId, SseEmitter emitter, Long snapshotSeq) {
    EmitterConnection previous =
        connections.put(userId, new EmitterConnection(emitter, snapshotSeq));
    return Optional.ofNullable(previous);
  }

  public Optional<SseEmitter> findByUserId(UUID userId) {
    return Optional.ofNullable(connections.get(userId)).map(EmitterConnection::emitter);
  }

  public Optional<Long> findSnapshotSeq(UUID userId) {
    return Optional.ofNullable(connections.get(userId)).map(EmitterConnection::snapshotSeq);
  }

  public void remove(UUID userId, SseEmitter emitter) {
    connections.computeIfPresent(userId,
        (id, current) -> current.emitter() == emitter ? null : current);
  }

  public Map<UUID, SseEmitter> findAll() {
    return connections.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().emitter()));
  }
}