package com.sprint.mission.otboo.external.llm.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class LlmException extends OtbooException {

  protected LlmException(HttpStatus status, String message, Map<String, Object> details) {
    super(status, message, details);
  }
}
