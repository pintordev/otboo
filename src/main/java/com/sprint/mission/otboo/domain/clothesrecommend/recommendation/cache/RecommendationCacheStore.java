package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.cache;

import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.metrics.RecommendationCacheMetrics;
import com.sprint.mission.otboo.external.llm.dto.LlmRecommendationCandidate;
import com.sprint.mission.otboo.external.llm.dto.LlmRecommendationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM이 고른 의상 ID 목록을 Redis에 캐싱한다.
 *
 * <p>캐시 키는 LLM 판단에 들어가는 입력 전부({@link LlmRecommendationContext})에서 파생되므로, 날씨·옷장·온도민감도 중 하나라도 바뀌면
 * 키가 함께 바뀐다. 별도의 무효화 로직이 필요 없는 이유다. TTL은 낡은 값을 막기 위한 것이 아니라 저장소 정리 목적이다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RecommendationCacheStore {

  static final String KEY_PREFIX = "recommendation:llm:";
  private static final Duration TTL = Duration.ofHours(3);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final RecommendationCacheMetrics cacheMetrics;

  public Optional<List<UUID>> find(LlmRecommendationContext context) {
    try {
      String json = redisTemplate.opsForValue().get(key(context));
      if (json == null) {
        cacheMetrics.countMiss();
        return Optional.empty();
      }
      List<UUID> clothesIds = List.of(objectMapper.readValue(json, UUID[].class));
      cacheMetrics.countHit();
      return Optional.of(clothesIds);
    } catch (DataAccessException | JacksonException e) {
      cacheMetrics.countLookupError();
      log.warn("추천 캐시 조회 실패, 캐시 없이 진행한다", e);
      return Optional.empty();
    }
  }

  public void save(LlmRecommendationContext context, List<UUID> clothesIds) {
    try {
      redisTemplate.opsForValue()
          .set(key(context), objectMapper.writeValueAsString(clothesIds), TTL);
    } catch (DataAccessException | JacksonException e) {
      cacheMetrics.countSaveError();
      log.warn("추천 캐시 저장 실패, 다음 요청에서 LLM을 다시 호출한다", e);
    }
  }

  private String key(LlmRecommendationContext context) {
    return KEY_PREFIX + sha256(objectMapper.writeValueAsString(canonicalize(context)));
  }

  /**
   * 후보 목록을 의상 ID 순으로 정렬한다. 후보를 읽어오는 쿼리에 {@code ORDER BY}가 없어 같은 옷장이라도 행 순서가 달라질 수 있는데, 그대로 두면 입력이
   * 동일한데 키만 달라져 캐시가 적중하지 않는다.
   */
  private LlmRecommendationContext canonicalize(LlmRecommendationContext context) {
    List<LlmRecommendationCandidate> sortedCandidates = context.candidates().stream()
        .sorted(Comparator.comparing(LlmRecommendationCandidate::clothesId))
        .toList();
    return new LlmRecommendationContext(
        context.temperature(), context.adjustedTemperature(), context.precipitationType(),
        context.windStrength(), context.sensitivity(), sortedCandidates);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다", e);
    }
  }
}
