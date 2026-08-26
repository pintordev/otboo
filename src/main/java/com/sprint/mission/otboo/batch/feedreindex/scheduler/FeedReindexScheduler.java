package com.sprint.mission.otboo.batch.feedreindex.scheduler;

import com.sprint.mission.otboo.batch.feedreindex.service.FeedReindexService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedReindexScheduler {

  private static final String LOCK_NAME = "FeedReindexBatchSchedulerLock";

  private final FeedReindexService feedReindexService;

  // 전체와 증분이 동시에 돌면 읽기 시점 차이로 오래된 문서가 최신을 덮을 수 있어
  // 같은 락으로 상호 배제한다. 증분이 스킵되어도 다음 실행이 lookback으로 덮는다.
  //
  // lockAtMostFor는 프로세스가 죽었을 때 락이 남는 것을 막는 안전장치일 뿐, 작업이 끝났는지는 보지 않는다.
  // 이 시간을 넘기면 락이 풀려 상호 배제가 깨진다.
  // ShedLock은 락 하나에 만료 시각 하나만 저장하므로 전체·증분이 다른 값을 가질 수 없고, 오래 걸리는 전체 재색인 기준으로 잡는다.
  //
  // PT2H 근거: 10만 건이면 chunk 500 기준 200청크. 청크당 1초로 잡아도 4분 안쪽이다.
  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${batch.feed-reindex.lock-at-most-for:PT2H}",
      lockAtLeastFor = "${batch.feed-reindex.lock-at-least-for:PT1M}")
  @Scheduled(cron = "0 0 5 * * SUN", zone = "Asia/Seoul")
  public void reindexAll() {
    feedReindexService.executeReindexAll();
  }

  // 전체 재색인(일요일 05:00)과 겹치지 않도록 정각이 아닌 30분에 실행한다.
  // lockAtMostFor는 위와 같은 값을 쓴다 — 같은 락 이름이라 먼저 잡은 쪽 값이 적용되므로 다르게 주면 실행 순서에 따라 결과가 달라진다.
  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${batch.feed-reindex.lock-at-most-for:PT2H}",
      lockAtLeastFor = "${batch.feed-reindex.incremental-lock-at-least-for:PT30S}")
  @Scheduled(cron = "0 30 * * * *", zone = "Asia/Seoul")
  public void reindexIncremental() {
    feedReindexService.executeIncrementalReindex();
  }
}
