package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 추천 LLM 결과 캐시의 적중 여부를 집계한다.
 *
 * <p>히트율은 {@code hit / (hit + miss + lookupError)}로 읽는다. 조회는 세 결과 중 정확히 하나만 기록하므로 요청 하나가
 * 분모에 두 번 잡히지 않는다.
 *
 * <p>저장 실패는 같은 요청에서 미적중 뒤에 발생할 수 있어 조회 오류와 따로 센다. 한 카운터로 합치면 그 요청이 미적중과 오류에 중복 계상돼 히트율이
 * 왜곡된다. 두 오류는 대응도 다르다 — 조회 실패는 캐시를 못 읽은 것이고, 저장 실패는 다음 요청에서 LLM을 다시 부르게 되는 것이다.
 */
@Component
public class RecommendationCacheMetrics {

  public static final String HIT = "recommendation.cache.hit";
  public static final String MISS = "recommendation.cache.miss";
  public static final String LOOKUP_ERROR = "recommendation.cache.lookup.error";
  public static final String SAVE_ERROR = "recommendation.cache.save.error";

  private final Counter hitCounter;
  private final Counter missCounter;
  private final Counter lookupErrorCounter;
  private final Counter saveErrorCounter;

  public RecommendationCacheMetrics(MeterRegistry registry) {
    this.hitCounter = Counter.builder(HIT)
        .description("추천 LLM 결과 캐시 적중 횟수")
        .register(registry);
    this.missCounter = Counter.builder(MISS)
        .description("추천 LLM 결과 캐시 미적중 횟수")
        .register(registry);
    this.lookupErrorCounter = Counter.builder(LOOKUP_ERROR)
        .description("추천 LLM 결과 캐시 조회 실패 횟수")
        .register(registry);
    this.saveErrorCounter = Counter.builder(SAVE_ERROR)
        .description("추천 LLM 결과 캐시 저장 실패 횟수")
        .register(registry);
  }

  public void countHit() {
    hitCounter.increment();
  }

  public void countMiss() {
    missCounter.increment();
  }

  public void countLookupError() {
    lookupErrorCounter.increment();
  }

  public void countSaveError() {
    saveErrorCounter.increment();
  }
}
