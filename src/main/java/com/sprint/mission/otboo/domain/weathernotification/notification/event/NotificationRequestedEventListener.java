package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationOutboxPayload;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationOutboxRepository;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class NotificationRequestedEventListener {

  private final NotificationOutboxRepository notificationOutboxRepository;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(NotificationRequestedEvent event) {
    NotificationOutboxPayload outboxPayload = new NotificationOutboxPayload(UUID.randomUUID(), event);
    String payload = objectMapper.writeValueAsString(outboxPayload);
    notificationOutboxRepository.save(
        NotificationOutbox.create(NotificationKafkaTopics.NOTIFICATION_REQUESTED, payload));
  }
}
