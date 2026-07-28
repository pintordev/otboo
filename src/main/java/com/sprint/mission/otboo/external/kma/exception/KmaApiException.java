package com.sprint.mission.otboo.external.kma.exception;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class KmaApiException extends KmaException {

  private static final String MESSAGE = "기상청 API 응답 처리에 실패했습니다.";
  private static final String UNKNOWN = "UNKNOWN";

  private KmaApiException(Map<String, Object> details) {
    super(HttpStatus.BAD_GATEWAY, MESSAGE, details);
  }

  public static KmaApiException of(String resultCode, String resultMsg) {
    return new KmaApiException(Map.of(
        "resultCode", Objects.toString(resultCode, UNKNOWN),
        "resultMsg", Objects.toString(resultMsg, UNKNOWN)));
  }
}