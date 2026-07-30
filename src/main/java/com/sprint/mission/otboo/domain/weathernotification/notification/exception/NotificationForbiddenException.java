package com.sprint.mission.otboo.domain.weathernotification.notification.exception;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class NotificationForbiddenException extends NotificationException {

  private static final String MESSAGE = "본인만 수행할 수 있습니다.";

  private NotificationForbiddenException(Map<String, Object> details) {
    super(HttpStatus.FORBIDDEN, MESSAGE, details);
  }

  public static NotificationForbiddenException receiverMismatch() {
    return new NotificationForbiddenException(Collections.emptyMap());
  }
}