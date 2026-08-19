package com.sprint.mission.otboo.external.llm.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class LlmApiException extends LlmException {

  private static final String MESSAGE = "LLM 응답 처리에 실패했습니다.";

  private LlmApiException(Map<String, Object> details) {
    super(HttpStatus.BAD_GATEWAY, MESSAGE, details);
  }

  public static LlmApiException callFailed(Throwable cause) {
    LlmApiException exception = new LlmApiException(Map.of());
    exception.initCause(cause);
    return exception;
  }

  public static LlmApiException parseFailed() {
    return new LlmApiException(Map.of());
  }
}
