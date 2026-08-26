package com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSearchResult;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.testcontainers.ElasticsearchTestContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;

@DataElasticsearchTest
@ActiveProfiles("test")
@DisplayName("FeedSearchCustomRepository")
class FeedSearchCustomRepositoryImplTest extends ElasticsearchTestContainerSupport {

  private static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Autowired
  private FeedSearchRepository feedSearchRepository;

  @Autowired
  private ElasticsearchOperations operations;

  private static void setField(Object target, String name, Object value) {
    try {
      var field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setUp() {
    var indexOps = operations.indexOps(FeedDocument.class);
    if (!indexOps.exists()) {
      indexOps.createWithMapping();
    }
    feedSearchRepository.deleteAll();
    indexOps.refresh();
  }

  @AfterEach
  void tearDown() {
    feedSearchRepository.deleteAll();
    operations.indexOps(FeedDocument.class).refresh();
  }

  // 인덱싱 후 즉시 검색되도록 refresh한다. ES는 기본 1초 주기로 인덱스를 갱신한다.
  private UUID indexFeed(UUID authorId, String content, SkyStatus sky,
      PrecipitationType precipitation, Instant createdAt, long likeCount,
      List<OotdSnapshot> ootds) {
    UUID feedId = UUID.randomUUID();
    WeatherSnapshot snapshot = new WeatherSnapshot(
        sky, precipitation, 0.0, 0.0, 28.0, 2.0, 16.0, 31.0);
    Feed feed = Feed.create(authorId, UUID.randomUUID(), content, snapshot, ootds);
    setField(feed, "id", feedId);
    setField(feed, "createdAt", createdAt);
    setField(feed, "updatedAt", createdAt);
    setField(feed, "likeCount", likeCount);

    feedSearchRepository.save(FeedDocument.from(feed));
    operations.indexOps(FeedDocument.class).refresh();
    return feedId;
  }

  private UUID indexFeed(UUID authorId, String content, SkyStatus sky,
      PrecipitationType precipitation, Instant createdAt, long likeCount) {
    return indexFeed(authorId, content, sky, precipitation, createdAt, likeCount, List.of());
  }

  private UUID indexFeed(String content, SkyStatus sky, PrecipitationType precipitation,
      Instant createdAt, long likeCount) {
    return indexFeed(UUID.randomUUID(), content, sky, precipitation, createdAt, likeCount);
  }

  private UUID indexFeedWithOotds(String content, List<String> ootdNames, Instant createdAt) {
    List<OotdSnapshot> ootds = ootdNames.stream()
        .map(name -> fm.giveMeBuilder(OotdSnapshot.class).set("name", name).sample())
        .toList();
    return indexFeed(UUID.randomUUID(), content, SkyStatus.CLEAR,
        PrecipitationType.NONE, createdAt, 0L, ootds);
  }

  private FeedListParams params(String keywordLike) {
    return new FeedListParams(null, null, 10,
        FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
        keywordLike, null, null, null);
  }

  @Nested
  @DisplayName("keywordLike 검색")
  class SearchByKeyword {

    @Test
    @DisplayName("검색어가 포함된 피드만 반환한다")
    void 검색어가_포함된_피드만_반환한다() {
      // given
      UUID matched = indexFeed("따뜻한 니트를 입었어요", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("시원한 반팔", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("니트"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
      assertThat(result.totalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("검색어가 없으면 전체를 반환한다")
    void 검색어가_없으면_전체를_반환한다() {
      // given
      indexFeed("따뜻한 니트", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("시원한 반팔", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params(null));

      // then
      assertThat(result.feedIds()).hasSize(2);
      assertThat(result.totalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("영문 대소문자를 구분하지 않고 검색된다")
    void 영문_대소문자를_구분하지_않고_검색된다() {
      // given
      UUID matched = indexFeed("맑은 날 OOTD", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("ootd"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }

    @Test
    @DisplayName("검색어가 빈 문자열이면 전체를 반환한다")
    void 검색어가_빈_문자열이면_전체를_반환한다() {
      // given
      indexFeed("따뜻한 니트", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("시원한 반팔", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params(""));

      // then
      assertThat(result.feedIds()).hasSize(2);
    }

    @Test
    @DisplayName("조건에 맞는 피드가 없으면 빈 목록을 반환한다")
    void 조건에_맞는_피드가_없으면_빈_목록을_반환한다() {
      // given
      indexFeed("따뜻한 니트", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("존재하지않는검색어"));

      // then
      assertThat(result.feedIds()).isEmpty();
      assertThat(result.totalCount()).isZero();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("검색어의 모든 토큰이 있는 피드만 반환한다")
    void 검색어의_모든_토큰이_있는_피드만_반환한다() {
      // given
      UUID matched = indexFeed("민트색 후드티 코디", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("카키색 야상 가을룩", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      indexFeed("검은색 트렌치코트 데일리룩", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("민트색"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
      assertThat(result.totalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("검색어 토큰의 75% 이상을 가진 피드만 반환한다")
    void 검색어_토큰의_75퍼센트_이상을_가진_피드만_반환한다() {
      // given
      UUID matched = indexFeed("니트에 야상 걸쳤어요", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("민트색 후드티 코디", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      indexFeed("따뜻한 니트를 입었어요", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("니트 야상 코디"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }

    @Test
    @DisplayName("복합명사는 분해된 토큰으로도 조회된다")
    void 복합명사는_분해된_토큰으로도_조회된다() {
      // given
      UUID first = indexFeed("니트에 청바지 조합", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      UUID second = indexFeed("베이지색 반팔티에 청바지 입었어요", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params("바지"));

      // then
      assertThat(result.feedIds()).containsExactlyInAnyOrder(first, second);
    }
  }

  @Nested
  @DisplayName("필터 조회")
  class SearchByFilter {

    @Test
    @DisplayName("skyStatusEqual이 주어지면 해당 하늘 상태의 피드만 반환한다")
    void skyStatusEqual이_주어지면_해당_하늘_상태의_피드만_반환한다() {
      // given
      UUID clear = indexFeed("맑은 날", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("흐린 날", SkyStatus.CLOUDY,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, SkyStatus.CLEAR, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(clear);
    }

    @Test
    @DisplayName("precipitationTypeEqual이 주어지면 해당 강수 유형의 피드만 반환한다")
    void precipitationTypeEqual이_주어지면_해당_강수_유형의_피드만_반환한다() {
      // given
      UUID rainy = indexFeed("비 오는 날", SkyStatus.CLOUDY,
          PrecipitationType.RAIN, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("맑은 날", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, PrecipitationType.RAIN, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(rainy);
    }

    @Test
    @DisplayName("authorIdEqual이 주어지면 해당 작성자의 피드만 반환한다")
    void authorIdEqual이_주어지면_해당_작성자의_피드만_반환한다() {
      // given
      UUID targetAuthor = UUID.randomUUID();
      UUID matched = indexFeed(targetAuthor, "대상 작성자 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("다른 작성자 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, targetAuthor);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }

    @Test
    @DisplayName("검색어와 필터를 함께 적용한다")
    void 검색어와_필터를_함께_적용한다() {
      // given
      UUID matched = indexFeed("따뜻한 니트", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("따뜻한 니트", SkyStatus.CLOUDY,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      indexFeed("시원한 반팔", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          "니트", SkyStatus.CLEAR, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }
  }

  @Nested
  @DisplayName("정렬")
  class SortFeeds {

    @Test
    @DisplayName("createdAt 내림차순으로 최신 피드를 먼저 반환한다")
    void createdAt_내림차순으로_최신_피드를_먼저_반환한다() {
      // given
      UUID older = indexFeed("오래된 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      UUID newer = indexFeed("최신 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);
      UUID middle = indexFeed("중간 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      // when
      FeedSearchResult result = feedSearchRepository.search(params(null));

      // then
      assertThat(result.feedIds()).containsExactly(newer, middle, older);
    }

    @Test
    @DisplayName("createdAt 오름차순으로 오래된 피드를 먼저 반환한다")
    void createdAt_오름차순으로_오래된_피드를_먼저_반환한다() {
      // given
      UUID older = indexFeed("오래된 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      UUID newer = indexFeed("최신 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);
      UUID middle = indexFeed("중간 글", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.ASCENDING,
          null, null, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(older, middle, newer);
    }

    @Test
    @DisplayName("likeCount 내림차순으로 좋아요가 많은 피드를 먼저 반환한다")
    void likeCount_내림차순으로_좋아요가_많은_피드를_먼저_반환한다() {
      // given — createdAt은 좋아요 순서와 어긋나게 둔다
      UUID low = indexFeed("좋아요 적음", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 1L);
      UUID high = indexFeed("좋아요 많음", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 100L);
      UUID mid = indexFeed("좋아요 중간", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 50L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.LIKE_COUNT, SortDirection.DESCENDING,
          null, null, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(high, mid, low);
    }

    @Test
    @DisplayName("likeCount 오름차순으로 좋아요가 적은 피드를 먼저 반환한다")
    void likeCount_오름차순으로_좋아요가_적은_피드를_먼저_반환한다() {
      // given
      UUID low = indexFeed("좋아요 적음", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 1L);
      UUID high = indexFeed("좋아요 많음", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 100L);
      UUID mid = indexFeed("좋아요 중간", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 50L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.LIKE_COUNT, SortDirection.ASCENDING,
          null, null, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).containsExactly(low, mid, high);
    }
  }

  @Nested
  @DisplayName("커서 페이지네이션")
  class SearchByCursor {

    @Test
    @DisplayName("limit보다 많으면 hasNext가 true이고 limit개만 반환한다")
    void limit보다_많으면_hasNext가_true이고_limit개만_반환한다() {
      // given
      indexFeed("첫번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("두번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      indexFeed("세번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.nextCursor()).isNotNull();
      assertThat(result.nextIdAfter()).isNotNull();
      assertThat(result.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("마지막 페이지면 hasNext가 false이고 커서를 비운다")
    void 마지막_페이지면_hasNext가_false이고_커서를_비운다() {
      // given
      indexFeed("첫번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      indexFeed("두번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);

      FeedListParams params = new FeedListParams(null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);

      // when
      FeedSearchResult result = feedSearchRepository.search(params);

      // then
      assertThat(result.feedIds()).hasSize(2);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("커서로 다음 페이지를 조회하면 이어서 반환한다")
    void 커서로_다음_페이지를_조회하면_이어서_반환한다() {
      // given
      UUID first = indexFeed("첫번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      UUID second = indexFeed("두번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      UUID third = indexFeed("세번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      FeedListParams firstPage = new FeedListParams(null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);

      // when: 첫 페이지 조회 — DESC이므로 third, second
      FeedSearchResult page1 = feedSearchRepository.search(firstPage);

      // then
      assertThat(page1.nextCursor()).isEqualTo("2026-08-02T00:00:00Z");
      assertThat(page1.feedIds()).containsExactly(third, second);

      // when: 커서로 다음 페이지 조회
      FeedListParams secondPage = new FeedListParams(
          page1.nextCursor(), page1.nextIdAfter(), 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);
      FeedSearchResult page2 = feedSearchRepository.search(secondPage);

      // then
      assertThat(page2.feedIds()).containsExactly(first);
      assertThat(page2.hasNext()).isFalse();
    }

    @Test
    @DisplayName("createdAt 동률에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void createdAt_동률에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      Instant sameTime = Instant.parse("2026-08-01T00:00:00Z");
      UUID a = indexFeed("A", SkyStatus.CLEAR, PrecipitationType.NONE, sameTime, 0L);
      UUID b = indexFeed("B", SkyStatus.CLEAR, PrecipitationType.NONE, sameTime, 0L);

      FeedListParams firstPage = new FeedListParams(null, null, 1,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);

      // when: 첫 페이지 조회
      FeedSearchResult page1 = feedSearchRepository.search(firstPage);

      // then
      assertThat(page1.feedIds()).hasSize(1);
      assertThat(page1.hasNext()).isTrue();
      UUID firstId = page1.feedIds().get(0);

      // when: 커서로 다음 페이지 조회
      FeedListParams secondPage = new FeedListParams(
          page1.nextCursor(), page1.nextIdAfter(), 1,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null, null, null);
      FeedSearchResult page2 = feedSearchRepository.search(secondPage);

      // then
      assertThat(page2.feedIds()).hasSize(1);
      UUID secondId = page2.feedIds().get(0);
      assertThat(secondId).isNotEqualTo(firstId);
      assertThat(List.of(firstId, secondId)).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("likeCount 정렬에서 커서로 다음 페이지를 조회하면 이어서 반환한다")
    void likeCount_정렬에서_커서로_다음_페이지를_조회하면_이어서_반환한다() {
      // given
      UUID low = indexFeed("A", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 10L);
      UUID high = indexFeed("B", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 100L);
      UUID mid = indexFeed("C", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 50L);

      FeedListParams firstPage = new FeedListParams(null, null, 2,
          FeedSortBy.LIKE_COUNT, SortDirection.DESCENDING,
          null, null, null, null);

      // when: 첫 페이지
      FeedSearchResult page1 = feedSearchRepository.search(firstPage);

      // then
      assertThat(page1.feedIds()).containsExactly(high, mid);
      assertThat(page1.nextCursor()).isEqualTo("50");

      // when: 커서로 다음 페이지 조회
      FeedListParams secondPage = new FeedListParams(
          page1.nextCursor(), page1.nextIdAfter(), 2,
          FeedSortBy.LIKE_COUNT, SortDirection.DESCENDING,
          null, null, null, null);
      FeedSearchResult page2 = feedSearchRepository.search(secondPage);

      // then
      assertThat(page2.feedIds()).containsExactly(low);
    }

    @Test
    @DisplayName("오름차순 정렬에서 커서로 다음 페이지를 조회하면 이어서 반환한다")
    void 오름차순_정렬에서_커서로_다음_페이지를_조회하면_이어서_반환한다() {
      // given
      UUID first = indexFeed("첫번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-01T00:00:00Z"), 0L);
      UUID second = indexFeed("두번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-02T00:00:00Z"), 0L);
      UUID third = indexFeed("세번째", SkyStatus.CLEAR,
          PrecipitationType.NONE, Instant.parse("2026-08-03T00:00:00Z"), 0L);

      FeedListParams firstPage = new FeedListParams(null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.ASCENDING,
          null, null, null, null);

      // when: 첫 페이지 — ASC이므로 first, second
      FeedSearchResult page1 = feedSearchRepository.search(firstPage);

      // then
      assertThat(page1.feedIds()).containsExactly(first, second);

      // when: 커서로 다음 페이지 조회
      FeedListParams secondPage = new FeedListParams(
          page1.nextCursor(), page1.nextIdAfter(), 2,
          FeedSortBy.CREATED_AT, SortDirection.ASCENDING,
          null, null, null, null);
      FeedSearchResult page2 = feedSearchRepository.search(secondPage);

      // then
      assertThat(page2.feedIds()).containsExactly(third);
      assertThat(page2.hasNext()).isFalse();
    }
  }

  @Nested
  @DisplayName("착장 검색")
  class SearchByOotd {

    @Test
    @DisplayName("본문에 없어도 착장 이름으로 검색된다")
    void 본문에_없어도_착장_이름으로_검색된다() {
      // given
      UUID matched = indexFeedWithOotds("오늘의 기록", List.of("민트색 후드티"),
          Instant.parse("2026-08-01T00:00:00Z"));
      indexFeedWithOotds("다른 기록", List.of("청바지"),
          Instant.parse("2026-08-02T00:00:00Z"));

      // when
      FeedSearchResult result = feedSearchRepository.search(params("후드티"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }

    @Test
    @DisplayName("착장이 없어도 본문으로 검색된다")
    void 착장이_없어도_본문으로_검색된다() {
      // given
      UUID matched = indexFeedWithOotds("민트색 후드티 코디", List.of(),
          Instant.parse("2026-08-01T00:00:00Z"));

      // when
      FeedSearchResult result = feedSearchRepository.search(params("후드티"));

      // then
      assertThat(result.feedIds()).containsExactly(matched);
    }
  }
}
