package com.sprint.mission.otboo.domain.weathernotification.sse.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseEmitterRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseMessageRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RequiredArgsConstructor
@Service
public class SseService {

  // SSE 연결 유지 시간(TIMEOUT)과 cleanUp() ping 주기(sse.clean-up.fixed-delay)는 함께
  // 조정돼야 한다. nginx proxy_read_timeout/ALB idle timeout 기본값은 60초라, 프록시가
  // 먼저 끊으면 TIMEOUT(30분) 설정은 의미가 없고 클라이언트는 1분마다 재연결하며 매번
  // 재생 로직을 태운다. 배포 환경의 프록시 idle timeout보다 ping 주기가 짧아야 한다.
  // (X-Accel-Buffering: no 헤더 필요 여부도 nginx 경유 시 함께 확인)
  private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();
  private static final String PING_EVENT_NAME = "ping";

  private final SseEmitterRepository sseEmitterRepository;
  private final SseMessageRepository sseMessageRepository;

  // 유저별 "emitter 등록 + 재생 스냅샷 확정"(connect)과 "메시지 저장 + 실시간 전송"(send)을
  // 하나의 상태 전이로 묶기 위한 락. 이 락 없이 두 작업이 같은 유저에 대해 겹치면, 그 경계에
  // 걸친 메시지가 실시간 전송과 재생에 모두 잡혀 중복 전송될 수 있다.
  private final ConcurrentHashMap<UUID, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();

  public SseEmitter connect(UUID userId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT);
    emitter.onCompletion(() -> sseEmitterRepository.remove(userId, emitter));
    emitter.onTimeout(() -> sseEmitterRepository.remove(userId, emitter));
    emitter.onError(e -> sseEmitterRepository.remove(userId, emitter));

    Instant snapshotAt;
    ReentrantLock lock = lockFor(userId);
    lock.lock();
    try {
      sseEmitterRepository.save(userId, emitter);
      snapshotAt = sseMessageRepository.getLatestCreatedAt();
    } finally {
      lock.unlock();
    }

    if (!ping(emitter)) {
      return emitter;
    }

    List<SseMessage> missed = sseMessageRepository.findAllAfter(lastEventId, userId);
    for (SseMessage message : missed) {
      if (snapshotAt != null && message.createdAt().isAfter(snapshotAt)) {
        break;
      }
      if (!sendToEmitter(emitter, message)) {
        break;
      }
    }
    return emitter;
  }

  public void send(List<NotificationDto> notificationDtos, String eventName) {
    notificationDtos.forEach(dto -> {
      SseMessage message = new SseMessage(Set.of(dto.receiverId()), eventName, dto);
      ReentrantLock lock = lockFor(dto.receiverId());
      lock.lock();
      try {
        sseMessageRepository.save(message);
        sseEmitterRepository.findByUserId(dto.receiverId())
            .ifPresent(emitter -> sendToEmitter(emitter, message));
      } finally {
        lock.unlock();
      }
    });
  }

  private ReentrantLock lockFor(UUID userId) {
    return connectionLocks.computeIfAbsent(userId, id -> new ReentrantLock());
  }

  public void disconnect(UUID userId) {
    sseEmitterRepository.findByUserId(userId)
        .ifPresent(SseEmitter::complete);
  }

  @Scheduled(fixedDelayString = "${sse.clean-up.fixed-delay}")
  public void cleanUp() {
    sseEmitterRepository.findAll().values()
        .forEach(this::ping);
  }

  private boolean sendToEmitter(SseEmitter emitter, SseMessage message) {
    try {
      emitter.send(SseEmitter.event()
          .id(message.id().toString())
          .name(message.eventName())
          .data(message.data()));
      return true;
    } catch (IOException | IllegalStateException e) {
      log.warn("sse send fail, closing emitter: messageId={}", message.id(), e);
      emitter.completeWithError(e);
      return false;
    }
  }

  private boolean ping(SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().name(PING_EVENT_NAME).data(""));
      return true;
    } catch (IOException | IllegalStateException e) {
      emitter.completeWithError(e);
      return false;
    }
  }
}