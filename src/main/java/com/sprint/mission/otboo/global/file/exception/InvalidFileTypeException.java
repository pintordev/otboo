package com.sprint.mission.otboo.global.file.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class InvalidFileTypeException extends FileException {

  private static final String MESSAGE = "허용되지 않는 파일 형식입니다.";

  private InvalidFileTypeException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.BAD_REQUEST, MESSAGE, details, cause);
  }

  public static InvalidFileTypeException withExtension(String extension) {
    return new InvalidFileTypeException(Map.of("extension", extension), null);
  }
}
