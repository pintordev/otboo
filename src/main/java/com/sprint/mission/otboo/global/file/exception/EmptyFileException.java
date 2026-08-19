package com.sprint.mission.otboo.global.file.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class EmptyFileException extends FileException {

  private static final String MESSAGE = "빈 파일은 업로드할 수 없습니다.";

  private EmptyFileException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.BAD_REQUEST, MESSAGE, details, cause);
  }

  public static EmptyFileException withNone() {
    return new EmptyFileException(Map.of(), null);
  }
}
