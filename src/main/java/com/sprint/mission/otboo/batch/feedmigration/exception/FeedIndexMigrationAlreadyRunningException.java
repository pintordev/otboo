package com.sprint.mission.otboo.batch.feedmigration.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 마이그레이션이 이미 진행 중이라 실행하지 않았음을 알린다.
 *
 * <p>{@code @SchedulerLock}이 락을 얻지 못하면 메서드를 건너뛰고 {@code null}을 반환한다.
 * 호출자가 성공으로 오해하지 않도록 409로 구분한다.
 */
public class FeedIndexMigrationAlreadyRunningException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.CONFLICT;
  private static final String MESSAGE = "인덱스 마이그레이션이 이미 진행 중입니다.";

  private FeedIndexMigrationAlreadyRunningException() {
    super(STATUS, MESSAGE, Map.of());
  }

  public static FeedIndexMigrationAlreadyRunningException occurred() {
    return new FeedIndexMigrationAlreadyRunningException();
  }
}
