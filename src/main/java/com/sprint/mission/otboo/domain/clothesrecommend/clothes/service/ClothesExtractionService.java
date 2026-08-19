package com.sprint.mission.otboo.domain.clothesrecommend.clothes.service;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesDto;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception.ClothesExtractionFailedException;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.mapper.ClothesMapper;
import com.sprint.mission.otboo.external.llm.LlmExtractionFetcher;
import com.sprint.mission.otboo.external.llm.dto.LlmExtractedClothesInfo;
import com.sprint.mission.otboo.external.llm.exception.LlmApiException;
import com.sprint.mission.otboo.external.purchase.PurchasePageParser;
import com.sprint.mission.otboo.external.purchase.dto.PurchasePageFetchResult;
import com.sprint.mission.otboo.external.purchase.exception.PurchasePageFetchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ClothesExtractionService {

  private final PurchasePageParser purchasePageParser;
  private final LlmExtractionFetcher llmExtractionFetcher;
  private final ClothesMapper clothesMapper;

  public ClothesDto extractByUrl(String url) {
    PurchasePageFetchResult result;
    try {
      result = purchasePageParser.fetchAndParse(url);
    } catch (PurchasePageFetchException e) {
      log.error("구매 페이지 요청 실패: url={}, 예외={}", url, e.getClass().getSimpleName());
      throw ClothesExtractionFailedException.pageFetchFailed(url, e);
    }

    if (!result.ogResult().isEmpty()) {
      return clothesMapper.toDto(result.ogResult());
    }

    log.info("OG 태그 파싱 실패, LLM 폴백 시도: {}", url);
    try {
      LlmExtractedClothesInfo info = llmExtractionFetcher.extract(result.html());
      return clothesMapper.toDto(info);
    } catch (LlmApiException e) {
      log.error("LLM 추출 실패: url={}, 예외={}", url, e.getClass().getSimpleName());
      throw ClothesExtractionFailedException.llmCallFailed(e);
    }
  }
}
