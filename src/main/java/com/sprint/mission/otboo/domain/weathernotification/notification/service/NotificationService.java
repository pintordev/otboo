package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.mapper.NotificationMapper;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  @Transactional
  public List<NotificationDto> create(NotificationRequestedEvent event) {
    List<Notification> notifications = event.receiverIds().stream()
        .map(receiverId -> Notification.create(receiverId, event.title(), event.content(), event.level()))
        .toList();
    return notificationRepository.saveAll(notifications).stream()
        .map(notificationMapper::toDto)
        .toList();
  }
}