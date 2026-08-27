package com.sprint.mission.otboo.domain.weathernotification.sse.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.sse.config.SseConfig;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.EmitterConnection;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseEmitterRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseMessageRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

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
  private static final int LOCK_STRIPES = 256;

  private final SseEmitterRepository sseEmitterRepository;
  private final SseMessageRepository sseMessageRepository;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  private final ReentrantLock[] connectionLocks = Stream.generate(ReentrantLock::new)
      .limit(LOCK_STRIPES)
      .toArray(ReentrantLock[]::new);

  public SseEmitter connect(UUID userId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT);
    emitter.onCompletion(() -> sseEmitterRepository.remove(userId, emitter));
    emitter.onTimeout(() -> sseEmitterRepository.remove(userId, emitter));
    emitter.onError(e -> sseEmitterRepository.remove(userId, emitter));

    Long snapshotSeq;
    Optional<EmitterConnection> previous;
    ReentrantLock lock = lockFor(userId);
    lock.lock();
    try {
      snapshotSeq = lastEventId == null ? null : sseMessageRepository.getLatestSequence();
      previous = sseEmitterRepository.save(userId, emitter, snapshotSeq);
    } finally {
      lock.unlock();
    }
    previous.ifPresent(p -> p.emitter().complete()); // 네트워크 IO는 락 밖에서

    if (!ping(emitter)) {
      return emitter;
    }

    List<SseMessage> missed = sseMessageRepository.findAllAfter(lastEventId, userId);
    for (SseMessage message : missed) {
      if (snapshotSeq != null && message.seq() > snapshotSeq) {
        break;
      }
      if (!sendToEmitter(emitter, message)) {
        break;
      }
    }
    return emitter;
  }

  public List<UUID> send(List<NotificationDto> notificationDtos, String eventName) {
    List<UUID> delivered = new ArrayList<>();
    notificationDtos.forEach(dto -> {
      try {
        SseMessage message =
            new SseMessage(Set.of(dto.receiverId()), eventName, dto, Instant.now(clock));
        long seq = sseMessageRepository.save(message);
        SseMessage withSeq = message.withSeq(seq);
        stringRedisTemplate.convertAndSend(SseConfig.SSE_CHANNEL,
            objectMapper.writeValueAsString(withSeq));
        delivered.add(dto.id());
      } catch (Exception e) {
        log.error("SSE 발행 실패: receiverId={}", dto.receiverId(), e);
      }
    });
    return delivered;
  }

  public void deliverLocally(SseMessage message) {
    message.receiverIds().forEach(receiverId -> {
      try {
        Optional<SseEmitter> emitter = resolveTargetEmitter(receiverId, message);
        emitter.ifPresent(e -> sendToEmitter(e, message));
      } catch (Exception e) {
        log.error("SSE 로컬 전달 실패: messageId={}", message.id(), e);
      }
    });
  }

  private Optional<SseEmitter> resolveTargetEmitter(UUID receiverId, SseMessage message) {
    ReentrantLock lock = lockFor(receiverId);
    lock.lock();
    try {
      Optional<Long> snapshotSeq = sseEmitterRepository.findSnapshotSeq(receiverId);
      // message.seq()가 null이면(롤링 배포 중 구버전 인스턴스가 발행한 메시지) 이미 재생됐는지
      // 판단할 근거가 없다 — 조용히 폐기하는 대신 그대로 전달한다(중복 전송 위험 < 유실 위험).
      boolean alreadyReplayed = message.seq() != null && snapshotSeq
          .filter(s -> message.seq() <= s)
          .isPresent();
      if (alreadyReplayed) {
        return Optional.empty();
      }
      return sseEmitterRepository.findByUserId(receiverId);
    } finally {
      lock.unlock();
    }
  }

  private ReentrantLock lockFor(UUID userId) {
    int index = Math.floorMod(userId.hashCode(), LOCK_STRIPES);
    return connectionLocks[index];
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
      log.warn("SSE 전송 실패, emitter를 종료한다: messageId={}", message.id(), e);
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