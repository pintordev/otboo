package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.service;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.cache.RecommendationCacheStore;
import com.sprint.mission.otboo.external.llm.LlmRecommendationFetcher;
import com.sprint.mission.otboo.external.llm.dto.LlmRecommendationContext;
import com.sprint.mission.otboo.external.llm.dto.LlmSelectedClothes;
import com.sprint.mission.otboo.external.llm.exception.LlmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LlmRecommendationRefiner {

  private final LlmRecommendationFetcher llmRecommendationFetcher;
  private final RecommendationCacheStore recommendationCacheStore;

  public List<Clothes> refine(LlmRecommendationContext context, List<Clothes> candidates,
      List<Clothes> fallback) {
    Map<UUID, Clothes> candidatesById = candidates.stream()
        .collect(Collectors.toMap(Clothes::getId, Function.identity()));

    Optional<List<Clothes>> cached = findCached(context, candidatesById);
    if (cached.isPresent()) {
      return cached.orElseThrow();
    }

    LlmSelectedClothes selected;
    try {
      selected = llmRecommendationFetcher.select(context);
    } catch (LlmException e) {
      log.error("LLM 추천 재선정 실패, 규칙 기반 결과로 대체", e);
      return fallback;
    }

    Optional<List<Clothes>> resolved = resolve(selected.clothesIds(), candidatesById);
    if (resolved.isEmpty()) {
      log.warn("LLM 선택이 검증을 통과하지 못함, 규칙 기반 결과로 대체");
      return fallback;
    }

    recommendationCacheStore.save(context, selected.clothesIds());
    return resolved.orElseThrow();
  }

  /**
   * 캐시에 남은 선택도 지금의 후보 목록으로 다시 검증한다. 캐시 키가 후보 전체에서 파생되므로 이론상 항상 유효하지만, 어긋난 값이 그대로 응답에 실리는 것보다 LLM을
   * 한 번 더 호출하는 편이 안전하다.
   */
  private Optional<List<Clothes>> findCached(LlmRecommendationContext context,
      Map<UUID, Clothes> candidatesById) {
    Optional<List<UUID>> cachedIds = recommendationCacheStore.find(context);
    if (cachedIds.isEmpty()) {
      return Optional.empty();
    }

    Optional<List<Clothes>> cached = resolve(cachedIds.orElseThrow(), candidatesById);
    if (cached.isEmpty()) {
      log.warn("캐시된 선택이 현재 후보와 맞지 않음, LLM을 다시 호출한다");
      return Optional.empty();
    }

    log.debug("LLM 추천 캐시 적중");
    return cached;
  }

  private Optional<List<Clothes>> resolve(List<UUID> clothesIds,
      Map<UUID, Clothes> candidatesById) {
    List<Clothes> resolved = new ArrayList<>();
    for (UUID clothesId : clothesIds) {
      Clothes clothes = candidatesById.get(clothesId);
      if (clothes == null) {
        log.warn("선택된 의상이 후보에 없음 clothesId={}", clothesId);
        return Optional.empty();
      }
      resolved.add(clothes);
    }

    if (hasDuplicateType(resolved)) {
      log.warn("선택에 동일 타입이 중복됨");
      return Optional.empty();
    }

    if (hasDressWithTopOrBottom(resolved)) {
      log.warn("선택에 DRESS와 TOP/BOTTOM이 동시에 포함됨");
      return Optional.empty();
    }

    return Optional.of(resolved);
  }

  private boolean hasDuplicateType(List<Clothes> clothesList) {
    Set<ClothesType> types = clothesList.stream().map(Clothes::getType).collect(Collectors.toSet());
    return types.size() != clothesList.size();
  }

  private boolean hasDressWithTopOrBottom(List<Clothes> clothesList) {
    Set<ClothesType> types = clothesList.stream().map(Clothes::getType).collect(Collectors.toSet());
    boolean hasDress = types.contains(ClothesType.DRESS);
    boolean hasTopOrBottom = types.contains(ClothesType.TOP) || types.contains(ClothesType.BOTTOM);
    return hasDress && hasTopOrBottom;
  }
}
