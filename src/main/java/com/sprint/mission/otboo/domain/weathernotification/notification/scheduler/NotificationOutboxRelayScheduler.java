package com.sprint.mission.otboo.domain.weathernotification.notification.scheduler;

import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationOutboxRelayService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationOutboxRelayScheduler {

  private static final String LOCK_NAME = "NotificationOutboxRelaySchedulerLock";

  private final NotificationOutboxRelayService notificationOutboxRelayService;

  // relay()는 발행 완료까지 하나의 트랜잭션+락 구간 안에서 동기로 처리한다(비동기로 쪼개면
  // 락이 실제 발행 완료 전에 풀려 다음 폴링이 같은 행을 중복 발행할 수 있음). 그래서
  // lockAtMostFor는 최악의 경우(batchSize × Kafka 전송 timeout)보다 반드시 커야 인스턴스 간
  // 중복 실행이 구조적으로 막힌다 — 기본값(batchSize 20 × timeout 5초 = 100초)보다 넉넉한 여유
  // 확보.
  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${notification.outbox-relay.lock-at-most-for:PT10M}",
      lockAtLeastFor = "${notification.outbox-relay.lock-at-least-for:PT1S}")
  @Scheduled(fixedDelayString = "${notification.outbox-relay.fixed-delay:3000}")
  public void relay() {
    notificationOutboxRelayService.relay();
  }
}