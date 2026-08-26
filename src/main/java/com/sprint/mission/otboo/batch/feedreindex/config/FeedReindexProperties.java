package com.sprint.mission.otboo.batch.feedreindex.config;

import com.sprint.mission.otboo.global.batch.BatchConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 피드 재색인 배치 설정
 *
 * <p>같은 {@code batch.feed-reindex} prefix를 쓰는 키 중 아래 셋은 {@code @SchedulerLock}의
 * 속성이 컴파일 타임 상수여야 해 이 record가 아니라 {@code FeedReindexScheduler}가 플레이스홀더로 직접 읽는다. 이 record와 달리
 * {@code @Validated} 검증을 받지 않으므로, yaml에 오타가 있어도 기동이 막히지 않고 조용히 기본값으로 동작한다.
 *
 * <ul>
 *   <li>{@code batch.feed-reindex.lock-at-most-for} (기본 PT2H)
 *   <li>{@code batch.feed-reindex.lock-at-least-for} (기본 ...)
 *   <li>{@code batch.feed-reindex.incremental-lock-at-least-for} (기본 ...)
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "batch.feed-reindex")
public record FeedReindexProperties(

    // ES bulk 단위. 피드 문서가 건당 1KB 미만이라 500건이어도 요청 크기에 여유가 있다.
    @DefaultValue("500") @Positive @Max(BatchConstants.MAX_CHUNK_SIZE) int chunkSize,
    @DefaultValue("10") @Positive @Max(BatchConstants.MAX_SKIP_LIMIT) int skipLimit,

    // 증분 재색인이 훑을 과거 구간
    // 행 주기(1시간)보다 넉넉히 잡아 한 번 걸러져도 다음 실행이 덮는다.
    @DefaultValue("PT2H") Duration incrementalLookback
) {

}
