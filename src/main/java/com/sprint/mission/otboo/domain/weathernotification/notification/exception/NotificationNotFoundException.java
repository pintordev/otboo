package com.sprint.mission.otboo.domain.weathernotification.notification.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends NotificationException {

  private static final String MESSAGE = "알림을 찾을 수 없습니다.";

  private NotificationNotFoundException(Map<String, Object> details) {
    super(HttpStatus.NOT_FOUND, MESSAGE, details);
  }

  public static NotificationNotFoundException withId(UUID notificationId) {
    return new NotificationNotFoundException(Map.of("notificationId", notificationId));
  }
}