package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.metrics.RecommendationCacheMetrics;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.external.llm.dto.LlmRecommendationCandidate;
import com.sprint.mission.otboo.external.llm.dto.LlmRecommendationContext;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class RecommendationCacheStoreTest implements RedisTestContainerSupport {

  private static final UUID TOP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BOTTOM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SHOES_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private RecommendationCacheStore cacheStore;
  private MeterRegistry meterRegistry;

  @BeforeAll
  static void setUpRedis() {
    connectionFactory = new LettuceConnectionFactory(
        REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void tearDownRedis() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    Set<String> keys = redisTemplate.keys(RecommendationCacheStore.KEY_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
    meterRegistry = new SimpleMeterRegistry();
    cacheStore = new RecommendationCacheStore(
        redisTemplate, new ObjectMapper(), new RecommendationCacheMetrics(meterRegistry));
  }

  private double count(String meterName) {
    return meterRegistry.counter(meterName).count();
  }

  private LlmRecommendationCandidate candidate(UUID id, String name, ClothesType type) {
    return new LlmRecommendationCandidate(id, name, type, "");
  }

  private LlmRecommendationContext context(List<LlmRecommendationCandidate> candidates) {
    return new LlmRecommendationContext(
        5.0, 3.5, PrecipitationType.RAIN, WindStrength.STRONG, 3, candidates);
  }

  private LlmRecommendationContext defaultContext() {
    return context(List.of(
        candidate(TOP_ID, "니트", ClothesType.TOP),
        candidate(BOTTOM_ID, "청바지", ClothesType.BOTTOM)));
  }

  @Nested
  @DisplayName("캐시 저장·조회")
  class SaveAndFind {

    @Test
    @DisplayName("저장한_뒤_같은_컨텍스트로_조회하면_저장된_의상_id_목록을_반환한다")
    void 저장한_뒤_같은_컨텍스트로_조회하면_저장된_의상_id_목록을_반환한다() {
      // given
      LlmRecommendationContext context = defaultContext();
      cacheStore.save(context, List.of(TOP_ID, BOTTOM_ID));

      // when
      Optional<List<UUID>> found = cacheStore.find(defaultContext());

      // then
      assertThat(found).contains(List.of(TOP_ID, BOTTOM_ID));
    }

    @Test
    @DisplayName("저장한_적_없는_컨텍스트로_조회하면_빈_Optional을_반환한다")
    void 저장한_적_없는_컨텍스트로_조회하면_빈_Optional을_반환한다() {
      // when
      Optional<List<UUID>> found = cacheStore.find(defaultContext());

      // then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("저장된_키에_TTL이_설정된다")
    void 저장된_키에_TTL이_설정된다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID));

      // when
      Set<String> keys = redisTemplate.keys(RecommendationCacheStore.KEY_PREFIX + "*");
      String key = keys.iterator().next();
      Long ttlSeconds = redisTemplate.getExpire(key);

      // then
      assertThat(ttlSeconds)
          .isPositive()
          .isLessThanOrEqualTo(Duration.ofHours(3).toSeconds());
    }
  }

  @Nested
  @DisplayName("캐시 키 결정성")
  class KeyDeterminism {

    @Test
    @DisplayName("후보_순서만_다른_컨텍스트는_같은_캐시를_사용한다")
    void 후보_순서만_다른_컨텍스트는_같은_캐시를_사용한다() {
      // given — DB가 ORDER BY 없이 반환해 후보 순서가 뒤집힌 상황
      cacheStore.save(defaultContext(), List.of(TOP_ID, BOTTOM_ID));
      LlmRecommendationContext reordered = context(List.of(
          candidate(BOTTOM_ID, "청바지", ClothesType.BOTTOM),
          candidate(TOP_ID, "니트", ClothesType.TOP)));

      // when
      Optional<List<UUID>> found = cacheStore.find(reordered);

      // then
      assertThat(found).contains(List.of(TOP_ID, BOTTOM_ID));
    }

    @Test
    @DisplayName("온도민감도가_다르면_캐시가_적중하지_않는다")
    void 온도민감도가_다르면_캐시가_적중하지_않는다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID, BOTTOM_ID));
      LlmRecommendationContext otherSensitivity = new LlmRecommendationContext(
          5.0, 3.5, PrecipitationType.RAIN, WindStrength.STRONG, 5,
          defaultContext().candidates());

      // when
      Optional<List<UUID>> found = cacheStore.find(otherSensitivity);

      // then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("후보_의상이_추가되면_캐시가_적중하지_않는다")
    void 후보_의상이_추가되면_캐시가_적중하지_않는다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID, BOTTOM_ID));
      LlmRecommendationContext withNewClothes = context(List.of(
          candidate(TOP_ID, "니트", ClothesType.TOP),
          candidate(BOTTOM_ID, "청바지", ClothesType.BOTTOM),
          candidate(SHOES_ID, "운동화", ClothesType.SHOES)));

      // when
      Optional<List<UUID>> found = cacheStore.find(withNewClothes);

      // then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("의상_이름이_수정되면_캐시가_적중하지_않는다")
    void 의상_이름이_수정되면_캐시가_적중하지_않는다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID, BOTTOM_ID));
      LlmRecommendationContext renamed = context(List.of(
          candidate(TOP_ID, "두꺼운 니트", ClothesType.TOP),
          candidate(BOTTOM_ID, "청바지", ClothesType.BOTTOM)));

      // when
      Optional<List<UUID>> found = cacheStore.find(renamed);

      // then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("캐시 장애 대응")
  class CacheFailureIsSafe {

    @Test
    @DisplayName("저장된_값이_깨져있으면_빈_Optional을_반환한다")
    void 저장된_값이_깨져있으면_빈_Optional을_반환한다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID));
      String key = redisTemplate.keys(RecommendationCacheStore.KEY_PREFIX + "*")
          .iterator().next();
      redisTemplate.opsForValue().set(key, "{깨진 JSON");

      // when
      Optional<List<UUID>> found = cacheStore.find(defaultContext());

      // then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Redis_조회가_실패하면_예외를_던지지_않고_빈_Optional을_반환한다")
    void Redis_조회가_실패하면_예외를_던지지_않고_빈_Optional을_반환한다() {
      // given
      StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      given(failingTemplate.opsForValue()).willReturn(valueOps);
      given(valueOps.get(anyString()))
          .willThrow(new RedisConnectionFailureException("redis down"));
      RecommendationCacheStore failingStore = new RecommendationCacheStore(
          failingTemplate, new ObjectMapper(), new RecommendationCacheMetrics(meterRegistry));

      // when
      Optional<List<UUID>> found = failingStore.find(defaultContext());

      // then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Redis_저장이_실패해도_예외를_던지지_않는다")
    void Redis_저장이_실패해도_예외를_던지지_않는다() {
      // given
      StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      given(failingTemplate.opsForValue()).willReturn(valueOps);
      willThrow(new RedisConnectionFailureException("redis down"))
          .given(valueOps).set(anyString(), anyString(), any(Duration.class));
      RecommendationCacheStore failingStore = new RecommendationCacheStore(
          failingTemplate, new ObjectMapper(), new RecommendationCacheMetrics(meterRegistry));

      // when & then
      assertThatCode(() -> failingStore.save(defaultContext(), List.of(TOP_ID)))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("캐시 계측")
  class Metrics {

    @Test
    @DisplayName("캐시에_적중하면_hit_카운터가_증가한다")
    void 캐시에_적중하면_hit_카운터가_증가한다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID, BOTTOM_ID));

      // when
      cacheStore.find(defaultContext());

      // then
      assertThat(count(RecommendationCacheMetrics.HIT)).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.MISS)).isZero();
      assertThat(count(RecommendationCacheMetrics.LOOKUP_ERROR)).isZero();
      assertThat(count(RecommendationCacheMetrics.SAVE_ERROR)).isZero();
    }

    @Test
    @DisplayName("캐시에_값이_없으면_miss_카운터가_증가한다")
    void 캐시에_값이_없으면_miss_카운터가_증가한다() {
      // when
      cacheStore.find(defaultContext());

      // then
      assertThat(count(RecommendationCacheMetrics.MISS)).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.HIT)).isZero();
    }

    @Test
    @DisplayName("저장된_값이_깨져있으면_조회_오류_카운터가_증가한다")
    void 저장된_값이_깨져있으면_조회_오류_카운터가_증가한다() {
      // given
      cacheStore.save(defaultContext(), List.of(TOP_ID));
      String key = redisTemplate.keys(RecommendationCacheStore.KEY_PREFIX + "*")
          .iterator().next();
      redisTemplate.opsForValue().set(key, "{깨진 JSON");

      // when
      cacheStore.find(defaultContext());

      // then
      assertThat(count(RecommendationCacheMetrics.LOOKUP_ERROR)).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.SAVE_ERROR)).isZero();
      assertThat(count(RecommendationCacheMetrics.HIT)).isZero();
    }

    @Test
    @DisplayName("Redis_조회가_실패하면_조회_오류_카운터만_증가한다")
    void Redis_조회가_실패하면_조회_오류_카운터만_증가한다() {
      // given
      StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      given(failingTemplate.opsForValue()).willReturn(valueOps);
      given(valueOps.get(anyString()))
          .willThrow(new RedisConnectionFailureException("redis down"));
      RecommendationCacheStore failingStore = new RecommendationCacheStore(
          failingTemplate, new ObjectMapper(), new RecommendationCacheMetrics(meterRegistry));

      // when
      failingStore.find(defaultContext());

      // then
      assertThat(count(RecommendationCacheMetrics.LOOKUP_ERROR)).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.SAVE_ERROR)).isZero();
      assertThat(count(RecommendationCacheMetrics.MISS)).isZero();
    }

    @Test
    @DisplayName("Redis_저장이_실패하면_저장_오류_카운터만_증가한다")
    void Redis_저장이_실패하면_저장_오류_카운터만_증가한다() {
      // given
      StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      given(failingTemplate.opsForValue()).willReturn(valueOps);
      willThrow(new RedisConnectionFailureException("redis down"))
          .given(valueOps).set(anyString(), anyString(), any(Duration.class));
      RecommendationCacheStore failingStore = new RecommendationCacheStore(
          failingTemplate, new ObjectMapper(), new RecommendationCacheMetrics(meterRegistry));

      // when
      failingStore.save(defaultContext(), List.of(TOP_ID));

      // then
      assertThat(count(RecommendationCacheMetrics.SAVE_ERROR)).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.LOOKUP_ERROR)).isZero();
      assertThat(count(RecommendationCacheMetrics.MISS)).isZero();
    }

    @Test
    @DisplayName("미적중_뒤_저장이_실패해도_히트율_분모가_중복되지_않는다")
    void 미적중_뒤_저장이_실패해도_히트율_분모가_중복되지_않는다() {
      // given — 조회는 정상(미적중), 저장만 실패하는 상황
      StringRedisTemplate partiallyFailingTemplate = mock(StringRedisTemplate.class);
      @SuppressWarnings("unchecked")
      ValueOperations<String, String> valueOps = mock(ValueOperations.class);
      given(partiallyFailingTemplate.opsForValue()).willReturn(valueOps);
      given(valueOps.get(anyString())).willReturn(null);
      willThrow(new RedisConnectionFailureException("redis down"))
          .given(valueOps).set(anyString(), anyString(), any(Duration.class));
      RecommendationCacheStore store = new RecommendationCacheStore(
          partiallyFailingTemplate, new ObjectMapper(),
          new RecommendationCacheMetrics(meterRegistry));

      // when — 요청 한 건: 조회 미적중 후 저장 실패
      store.find(defaultContext());
      store.save(defaultContext(), List.of(TOP_ID));

      // then — 히트율 분모(hit + miss + lookupError)에 이 요청이 한 번만 잡힌다
      double denominator = count(RecommendationCacheMetrics.HIT)
          + count(RecommendationCacheMetrics.MISS)
          + count(RecommendationCacheMetrics.LOOKUP_ERROR);
      assertThat(denominator).isEqualTo(1.0);
      assertThat(count(RecommendationCacheMetrics.SAVE_ERROR)).isEqualTo(1.0);
    }
  }
}
