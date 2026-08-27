package com.sprint.mission.otboo.batch.feedmigration.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class FeedIndexNameException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
  private static final String MESSAGE = "피드 인덱스 이름이 규칙에 맞지 않습니다.";

  private FeedIndexNameException(Map<String, Object> details) {
    super(STATUS, MESSAGE, details);
  }

  public static FeedIndexNameException of(String indexName, String expectedPattern) {
    return new FeedIndexNameException(
        Map.of("indexName", String.valueOf(indexName), "expectedPattern", expectedPattern));
  }
}
