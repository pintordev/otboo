package com.sprint.mission.otboo.global.file.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class FileException extends OtbooException {

  protected FileException(HttpStatus status, String message, Map<String, Object> details,
      Throwable cause) {
    super(status, message, details, cause);
  }
}
