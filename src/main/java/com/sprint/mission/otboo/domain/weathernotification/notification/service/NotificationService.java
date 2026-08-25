package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.exception.NotificationForbiddenException;
import com.sprint.mission.otboo.domain.weathernotification.notification.exception.NotificationNotFoundException;
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
  public List<NotificationDto> create(UUID eventId, NotificationRequestedEvent event) {
    List<Notification> notifications = event.receiverIds().stream()
        .filter(receiverId -> !notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId))
        .map(receiverId -> Notification.create(
            eventId, receiverId, event.title(), event.content(), event.level()))
        .toList();
    if (notifications.isEmpty()) {
      return List.of();
    }
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

  @Transactional
  public void delete(UUID notificationId, UUID currentUserId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> NotificationNotFoundException.withId(notificationId));
    if (!notification.getReceiverId().equals(currentUserId)) {
      throw NotificationForbiddenException.receiverMismatch();
    }
    notificationRepository.delete(notification);
    log.info("알림 삭제 완료: notificationId={}", notificationId);
  }
}