package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.mapper.NotificationMapper;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

  public CursorPageResponse<NotificationDto> getNotifications(
      UUID receiverId, NotificationListParams params) {
    CursorPageResponse<NotificationDto> result =
        notificationRepository.findNotifications(receiverId, params);
    log.info("알림 목록 조회 완료: 조회 건수={}, hasNext={}", result.data().size(), result.hasNext());
    return result;
  }
}