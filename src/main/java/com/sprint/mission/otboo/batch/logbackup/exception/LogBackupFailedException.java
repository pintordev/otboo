package com.sprint.mission.otboo.batch.logbackup.exception;

public class LogBackupFailedException extends RuntimeException {

  private LogBackupFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public static LogBackupFailedException withKey(String s3Key, Throwable cause) {
    return new LogBackupFailedException("로그 백업 실패: s3Key=" + s3Key, cause);
  }
}
