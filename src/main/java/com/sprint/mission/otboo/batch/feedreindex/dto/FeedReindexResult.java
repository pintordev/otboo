package com.sprint.mission.otboo.batch.feedreindex.dto;

/**
 * 재색인 실행 결과.
 *
 * @param readCount     DB에서 읽은 피드 수
 * @param writeCount    인덱스에 쓴 문서 수
 * @param elapsedMillis 소요 시간
 */
public record FeedReindexResult(
    long readCount,
    long writeCount,
    long elapsedMillis
) {

}
