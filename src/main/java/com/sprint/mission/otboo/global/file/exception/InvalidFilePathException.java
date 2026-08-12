package com.sprint.mission.otboo.global.file.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidFilePathException extends FileException {

  private static final String MESSAGE = "허용되지 않는 파일 경로입니다.";

  private InvalidFilePathException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.BAD_REQUEST, MESSAGE, details, cause);
  }

  public static InvalidFilePathException withNone() {
    return new InvalidFilePathException(Map.of(), null);
  }
}
