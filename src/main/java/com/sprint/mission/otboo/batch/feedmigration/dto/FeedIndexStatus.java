package com.sprint.mission.otboo.batch.feedmigration.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 피드 검색 인덱스의 현재 상태.
 *
 * @param aliasName       alias 이름
 * @param currentIndex    alias가 가리키는 실제 인덱스. alias가 아니면 null
 * @param alias           {@code feeds}가 alias인지 여부. false면 전환이 필요하다
 * @param missingFields   FeedDocument가 기대하지만 실제 매핑에 없는 필드
 * @param indexedCount    인덱스에 색인된 문서 수
 * @param feedCount       DB의 삭제되지 않은 피드 수
 * @param lastReindexAt   마지막으로 완료된 전체 재색인 시각. 실행 이력이 없으면 null
 * @param lastMigrationAt 마지막으로 완료된 매핑 마이그레이션 시각. 실행 이력이 없으면 null
 */
public record FeedIndexStatus(
    String aliasName,
    String currentIndex,
    boolean alias,
    Set<String> missingFields,
    long indexedCount,
    long feedCount,
    Instant lastReindexAt,
    Instant lastMigrationAt
) {

}
