package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
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

  @KafkaListener(topics = NotificationKafkaTopics.NOTIFICATION_REQUESTED,
      groupId = "notification-requested-consumer")
  public void consume(String payload) {
    NotificationRequestedEvent event = objectMapper.readValue(payload, NotificationRequestedEvent.class);
    List<NotificationDto> notificationDtos = notificationService.create(event);
    sseService.send(notificationDtos, "notifications");
  }
}