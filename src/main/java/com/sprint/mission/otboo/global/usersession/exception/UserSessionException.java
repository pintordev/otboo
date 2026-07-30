package com.sprint.mission.otboo.global.usersession.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class UserSessionException extends OtbooException {

  protected UserSessionException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}
