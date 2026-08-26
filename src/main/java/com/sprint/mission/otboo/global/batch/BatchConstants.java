package com.sprint.mission.otboo.global.batch;

/**
 * 배치 Job과 그 Job이 쓰는 Repository가 공용으로 참조하는 상수 모음.
 */
public final class BatchConstants {

  // 배치 청크 사이즈 상한. 커서 조회 limit도 이 값으로 함께 clamp한다 — chunkSize가 이보다 크게 설정되면
  // Repository는 조회 limit만 clamp할 뿐 Batch 커밋 단위(청크)는 그대로 커져서 상한 없이 무의미해진다.
  public static final int MAX_CHUNK_SIZE = 1000;

  public static final int MAX_SKIP_LIMIT = 100;

  private BatchConstants() {
  }
}
