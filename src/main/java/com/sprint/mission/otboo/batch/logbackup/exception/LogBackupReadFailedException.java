package com.sprint.mission.otboo.batch.logbackup.exception;

/**
 * CloudWatch Logs 조회 실패를 나타낸다.
 *
 * <p>{@code LogBackupReader}는 실패 시 {@code nextToken}/{@code currentTargetDone} 등 페이지네이션
 * 상태를 전진시키지 않으므로, 이 예외를 skip 대상으로 등록하면 Spring Batch가 다음 {@code read()}를
 * 호출할 때 동일한 요청을 그대로 반복해 {@code skipLimit}만 소모하고 진행이 없다. 그래서
 * {@code LogBackupFailedException}과 분리해 {@code LogBackupJobConfig}의 retry/skip 대상에서
 * 제외하고, 발생 즉시 Step(Job)이 실패하도록 둔다.
 */
public class LogBackupReadFailedException extends RuntimeException {

  private LogBackupReadFailedException(Throwable cause) {
    super("로그 백업 실패: CloudWatch Logs 조회 중 오류", cause);
  }

  public static LogBackupReadFailedException wrap(Throwable cause) {
    return new LogBackupReadFailedException(cause);
  }
}
