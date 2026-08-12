package com.sprint.mission.otboo.global.file.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class FileTooLargeException extends FileException {

  private static final String MESSAGE = "파일 크기가 허용된 용량을 초과했습니다.";

  private FileTooLargeException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.CONTENT_TOO_LARGE, MESSAGE, details, cause);
  }

  public static FileTooLargeException withSize(long actualSize, long maxSize) {
    return new FileTooLargeException(Map.of("actualSize", actualSize, "maxSize", maxSize), null);
  }
}
