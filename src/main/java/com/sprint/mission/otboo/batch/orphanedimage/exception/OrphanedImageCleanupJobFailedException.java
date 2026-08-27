package com.sprint.mission.otboo.batch.orphanedimage.exception;

public class OrphanedImageCleanupJobFailedException extends RuntimeException {

  private OrphanedImageCleanupJobFailedException(Throwable cause) {
    super("유실 이미지 정리 배치 실행 실패", cause);
  }

  public static OrphanedImageCleanupJobFailedException wrap(Throwable cause) {
    return new OrphanedImageCleanupJobFailedException(cause);
  }
}
