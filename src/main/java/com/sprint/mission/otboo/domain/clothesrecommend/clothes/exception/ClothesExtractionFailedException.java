package com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ClothesExtractionFailedException extends ClothesException {

  private static final HttpStatus STATUS = HttpStatus.INTERNAL_SERVER_ERROR;

  private ClothesExtractionFailedException(String message, Map<String, Object> details,
      Throwable cause) {
    super(STATUS, message, details, cause);
  }

  private ClothesExtractionFailedException(String message, Map<String, Object> details) {
    super(STATUS, message, details);
  }

  public static ClothesExtractionFailedException pageFetchFailed(String url, Throwable cause) {
    return new ClothesExtractionFailedException(
        "구매 페이지를 불러올 수 없습니다.",
        Map.of("url", url),
        cause
    );
  }

  public static ClothesExtractionFailedException llmCallFailed(Throwable cause) {
    return new ClothesExtractionFailedException(
        "LLM 호출에 실패했습니다.",
        Map.of(),
        cause
    );
  }

  public static ClothesExtractionFailedException llmParseFailed() {
    return new ClothesExtractionFailedException(
        "LLM 응답을 파싱할 수 없습니다.",
        Map.of()
    );
  }
}
