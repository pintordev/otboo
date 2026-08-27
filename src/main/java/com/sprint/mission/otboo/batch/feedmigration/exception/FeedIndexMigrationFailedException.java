package com.sprint.mission.otboo.batch.feedmigration.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class FeedIndexMigrationFailedException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
  private static final String MESSAGE = "피드 인덱스 마이그레이션에 실패했습니다.";

  private FeedIndexMigrationFailedException(Map<String, Object> details, Throwable cause) {
    super(STATUS, MESSAGE, details, cause);
  }

  private FeedIndexMigrationFailedException(Map<String, Object> details) {
    super(STATUS, MESSAGE, details);
  }

  public static FeedIndexMigrationFailedException wrap(Throwable cause) {
    return new FeedIndexMigrationFailedException(Map.of(), cause);
  }

  public static FeedIndexMigrationFailedException jobNotCompleted(String status) {
    return new FeedIndexMigrationFailedException(Map.of("jobStatus", status));
  }
}
