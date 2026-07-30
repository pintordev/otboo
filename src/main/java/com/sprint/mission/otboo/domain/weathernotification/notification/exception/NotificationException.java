package com.sprint.mission.otboo.domain.weathernotification.notification.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class NotificationException extends OtbooException {

  protected NotificationException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}