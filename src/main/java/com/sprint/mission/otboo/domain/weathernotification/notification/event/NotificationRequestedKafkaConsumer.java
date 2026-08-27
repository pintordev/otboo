package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.exception.NotificationSseDeliveryFailedException;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationOutboxPayload;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class NotificationRequestedKafkaConsumer {

  private final NotificationService notificationService;
  private final SseService sseService;
  private final ObjectMapper objectMapper;

  @KafkaListener(id = "notificationRequestedConsumer",
      topics = NotificationKafkaTopics.NOTIFICATION_REQUESTED,
      groupId = "notification-requested-consumer")
  public void consume(String payload) {
    NotificationOutboxPayload outboxPayload = objectMapper.readValue(payload, NotificationOutboxPayload.class);
    List<NotificationDto> notificationDtos = notificationService
        .createAndFindUndelivered(outboxPayload.eventId(), outboxPayload.event());
    if (notificationDtos.isEmpty()) {
      return;
    }
    List<UUID> deliveredIds = sseService.send(notificationDtos, "notifications");
    notificationService.markSseDelivered(deliveredIds);
    if (deliveredIds.size() < notificationDtos.size()) {
      List<UUID> failedIds = notificationDtos.stream()
          .map(NotificationDto::id)
          .filter(id -> !deliveredIds.contains(id))
          .toList();
      throw NotificationSseDeliveryFailedException.of(failedIds);
    }
  }
}