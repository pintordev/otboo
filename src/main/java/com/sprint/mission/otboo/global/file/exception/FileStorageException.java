package com.sprint.mission.otboo.global.file.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class FileStorageException extends FileException {

  private static final String MESSAGE = "파일 저장에 실패했습니다.";

  private FileStorageException(Map<String, Object> details, Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE, details, cause);
  }

  public static FileStorageException withCause(Throwable cause) {
    return new FileStorageException(Map.of(), cause);
  }
}
