package com.sprint.mission.otboo.batch.feedreindex.exception;

import com.sprint.mission.otboo.global.exception.OtbooException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 재색인이 이미 진행 중이라 실행하지 않았음을 알린다.
 *
 * <p>스케줄 실행과 수동 실행이 겹치면 Spring Batch가 같은 Job의 중복 실행을 막는다.
 * 호출자가 실패로 오해하지 않도록 409로 구분한다.
 */
public class FeedReindexAlreadyRunningException extends OtbooException {

  private static final HttpStatus STATUS = HttpStatus.CONFLICT;
  private static final String MESSAGE = "피드 재색인이 이미 진행 중입니다.";

  private FeedReindexAlreadyRunningException(Throwable cause) {
    super(STATUS, MESSAGE, Map.of(), cause);
  }

  public static FeedReindexAlreadyRunningException wrap(Throwable cause) {
    return new FeedReindexAlreadyRunningException(cause);
  }
}
