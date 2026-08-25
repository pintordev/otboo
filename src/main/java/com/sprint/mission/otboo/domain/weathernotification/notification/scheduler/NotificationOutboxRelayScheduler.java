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

  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${notification.outbox-relay.lock-at-most-for:PT1M}",
      lockAtLeastFor = "${notification.outbox-relay.lock-at-least-for:PT1S}")
  @Scheduled(fixedDelayString = "${notification.outbox-relay.fixed-delay:3000}")
  public void relay() {
    notificationOutboxRelayService.relay();
  }
}