package com.sprint.mission.otboo.batch.logbackup.exception;

public class LogBackupJobFailedException extends RuntimeException {

  private LogBackupJobFailedException(Throwable cause) {
    super("로그 백업 배치 실행 실패", cause);
  }

  public static LogBackupJobFailedException wrap(Throwable cause) {
    return new LogBackupJobFailedException(cause);
  }
}
