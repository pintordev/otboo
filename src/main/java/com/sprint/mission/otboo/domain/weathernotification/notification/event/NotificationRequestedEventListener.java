package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
@Component
public class NotificationRequestedEventListener {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(NotificationRequestedEvent event) {
    try {
      String payload = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(NotificationKafkaTopics.NOTIFICATION_REQUESTED, payload)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("알림 요청 이벤트 Kafka 발행 실패: event={}", event, ex);
            }
          });
    } catch (JacksonException e) {
      log.error("알림 요청 이벤트 직렬화 실패: event={}", event, e);
    }
  }
}