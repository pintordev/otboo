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
  // lockAtMostFor는 최악의 경우보다 반드시 커야 인스턴스 간 중복 실행이 구조적으로 막힌다.
  // 건당 최악 대기 시간은 KafkaTemplate.send() 자체가 get() 타임아웃과 별개로 블로킹될 수
  // 있는 producer max.block.ms(5초로 제한, application.yaml)까지 더해서 잡는다 — batchSize
  // 20 × (max.block.ms 5초 + get() 타임아웃 5초) = 200초(CodeRabbit 리뷰, PR #256:
  // max.block.ms를 안 잡으면 기본값 60초라 20 × 65초 = 1,300초까지 벌어져 PT10M을 초과함).
  // 기본값(PT10M=600초)이 이보다 넉넉한 여유 확보.
  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${notification.outbox-relay.lock-at-most-for:PT10M}",
      lockAtLeastFor = "${notification.outbox-relay.lock-at-least-for:PT1S}")
  @Scheduled(fixedDelayString = "${notification.outbox-relay.fixed-delay:3000}")
  public void relay() {
    notificationOutboxRelayService.relay();
  }
}