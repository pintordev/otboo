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
import java.time.Clock;
import java.time.Instant;
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
  private final NotificationBatchWriter notificationBatchWriter;
  private final Clock clock;

  @Transactional
  public List<NotificationDto> createAndFindUndelivered(UUID eventId, NotificationRequestedEvent event) {
    List<Notification> newlyCreated = event.receiverIds().stream()
        .filter(receiverId -> !notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId))
        .map(receiverId -> Notification.create(
            eventId, receiverId, event.title(), event.content(), event.level()))
        .toList();
    notificationBatchWriter.saveAll(newlyCreated);
    return notificationRepository.findByEventIdAndSseDeliveredAtIsNull(eventId).stream()
        .map(notificationMapper::toDto)
        .toList();
  }

  @Transactional
  public void markSseDelivered(List<UUID> notificationIds) {
    if (notificationIds.isEmpty()) {
      return;
    }
    Instant deliveredAt = Instant.now(clock);
    notificationRepository.findAllById(notificationIds)
        .forEach(notification -> notification.markSseDelivered(deliveredAt));
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