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

  public static KmaApiException wrap(Throwable cause) {
    // 원인 예외 정보(causeType/causeMessage)는 details에 담지 않는다 - details는
    // GlobalExceptionHandler를 거쳐 공개 ErrorResponse로 그대로 직렬화되므로, 내부 장애/외부
    // 요청 정보가 노출될 수 있다. 원인은 initCause()로만 보관해 서버 로그에서만 확인한다.
    KmaApiException exception = new KmaApiException(Map.of());
    exception.initCause(cause);
    return exception;
  }
}