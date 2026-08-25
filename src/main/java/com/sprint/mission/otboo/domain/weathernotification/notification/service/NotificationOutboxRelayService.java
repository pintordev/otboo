package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutboxStatus;
import com.sprint.mission.otboo.domain.weathernotification.notification.properties.NotificationOutboxRelayProperties;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class NotificationOutboxRelayService {

  private static final long SEND_TIMEOUT_SECONDS = 5L;

  private final NotificationOutboxRepository notificationOutboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final NotificationOutboxRelayProperties notificationOutboxRelayProperties;
  private final Clock clock;

  @Transactional
  public void relay() {
    List<NotificationOutbox> pendingOutboxes = notificationOutboxRepository
        .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING,
            PageRequest.of(0, notificationOutboxRelayProperties.batchSize()));
    for (NotificationOutbox outbox : pendingOutboxes) {
      publish(outbox);
    }
  }

  private void publish(NotificationOutbox outbox) {
    try {
      kafkaTemplate.send(outbox.getTopic(), outbox.getPayload())
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      outbox.markPublished(Instant.now(clock));
      notificationOutboxRepository.save(outbox);
    } catch (Exception e) {
      log.error("outbox 발행 실패: id={}, topic={}", outbox.getId(), outbox.getTopic(), e);
    }
  }
}