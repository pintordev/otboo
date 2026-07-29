package com.sprint.mission.otboo.global.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidSortByException extends OtbooException {

  private InvalidSortByException(Map<String, Object> details) {
    super(HttpStatus.BAD_REQUEST, "지원하는 정렬 기준이 아닙니다.", details);
  }

  public static InvalidSortByException withValue(String value) {
    return new InvalidSortByException(Map.of("value", value));
  }
}