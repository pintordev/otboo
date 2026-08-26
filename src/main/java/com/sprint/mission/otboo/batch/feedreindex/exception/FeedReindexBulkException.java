package com.sprint.mission.otboo.batch.feedreindex.exception;

/**
 * bulk 색인에서 버전 충돌(409)이 아닌 실패가 발생했을 때 던진다.
 *
 * <p>버전 충돌은 더 최신 문서가 이미 색인됐다는 뜻이라 Writer가 정상 결과로 처리하므로
 * 이 예외로 올라오지 않는다. 개별 실패 내역은 로그로 남긴다.
 */
public class FeedReindexBulkException extends RuntimeException {

  private FeedReindexBulkException(int failureCount) {
    super("피드 재색인 bulk 색인 실패: " + failureCount + "건");
  }

  public static FeedReindexBulkException of(int failureCount) {
    return new FeedReindexBulkException(failureCount);
  }
}
