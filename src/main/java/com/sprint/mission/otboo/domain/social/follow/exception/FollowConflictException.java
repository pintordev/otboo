package com.sprint.mission.otboo.domain.social.follow.exception;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class FollowConflictException extends FollowException {

  private static final String MESSAGE = "동시 요청으로 처리하지 못했습니다. 다시 시도해주세요.";

  private FollowConflictException(Map<String, Object> details) {
    super(HttpStatus.CONFLICT, MESSAGE, details);
  }

  public static FollowConflictException withNone() {
    return new FollowConflictException(Collections.emptyMap());
  }
}