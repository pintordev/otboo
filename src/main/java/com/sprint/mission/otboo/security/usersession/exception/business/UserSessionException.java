package com.sprint.mission.otboo.security.usersession.exception.business;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class UserSessionException extends OtbooException {

  protected UserSessionException(HttpStatus status, String message,
      Map<String, Object> details, Throwable cause) {
    super(status, message, details, cause);
  }
}
