package com.sprint.mission.otboo.batch.feedmigration.dto;

/**
 * 마이그레이션 실행 결과.
 *
 * @param fromIndex     전환 전 인덱스
 * @param toIndex       전환 후 인덱스
 * @param indexedCount  새 인덱스에 색인한 문서 수
 * @param elapsedMillis 소요 시간
 */
public record FeedIndexMigrationResult(
    String fromIndex,
    String toIndex,
    long indexedCount,
    long elapsedMillis
) {

}
