package com.sprint.mission.otboo.domain.social.feed.repository.querydsl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("FeedCustomRepository")
class FeedCustomRepositoryTest {

  // 테스트용 더미 스냅샷 — 실제 날씨 값은 Repository 테스트와 무관
  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private User persistUser(String name) {
    return testEntityManager.persist(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  private Feed createAndSaveFeed(UUID authorId, String content) {
    return feedRepository.save(
        Feed.create(authorId, UUID.randomUUID(), content, DUMMY_SNAPSHOT, List.of()));
  }

  private Feed createAndSaveFeed(String content) {
    return createAndSaveFeed(persistUser("작성자").getId(), content);
  }

  private void setLikeCount(UUID feedId, long count) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update feeds set like_count = :count where id = :id")
        .setParameter("count", count)
        .setParameter("id", feedId)
        .executeUpdate();
  }

  private void setCreatedAt(UUID feedId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update feeds set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", feedId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("findFeeds")
  class FindFeeds {

    @Test
    @DisplayName("커서가 없으면 limit + 1개까지 조회한다")
    void 커서가_없으면_limit_플러스_1개까지_조회한다() {
      // given
      for (int i = 0; i < 3; i++) {
        createAndSaveFeed("내용" + i);
      }
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("커서 이후의 피드만 조회한다")
    void 커서_이후의_피드만_조회한다() {
      // given
      Feed first = createAndSaveFeed("첫번째");
      Feed second = createAndSaveFeed("두번째");
      Feed third = createAndSaveFeed("세번째");
      testEntityManager.flush();

      setCreatedAt(first.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(second.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(third.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      // DESC: 세번째(t3) → 두번째(t2) → 첫번째(t1), 커서 = third(t3)
      FeedListParams params = new FeedListParams(
          Instant.parse("2026-07-28T00:00:03Z").toString(), third.getId(), 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly("두번째", "첫번째");
    }

    @Test
    @DisplayName("authorIdEqual이 주어지면 해당 작성자의 피드만 조회한다")
    void authorIdEqual이_주어지면_해당_작성자의_피드만_조회한다() {
      // given
      UUID targetAuthor = persistUser("대상작성자").getId();
      UUID otherAuthor = persistUser("다른작성자").getId();
      createAndSaveFeed(targetAuthor, "대상 작성자 글");
      createAndSaveFeed(otherAuthor, "다른 작성자 글");
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, targetAuthor);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getAuthorId)
          .containsExactly(targetAuthor);
    }

    @Test
    @DisplayName("keywordLike가 주어지면 content에 해당 키워드를 포함한 피드만 조회한다")
    void keywordLike가_주어지면_content에_해당_키워드를_포함한_피드만_조회한다() {
      // given
      createAndSaveFeed("맑은 날 OOTD");
      createAndSaveFeed("비 오는 날 OOTD");
      createAndSaveFeed("흐린 날 패션");
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          "OOTD", null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactlyInAnyOrder("맑은 날 OOTD", "비 오는 날 OOTD");
    }

    @Test
    @DisplayName("sortBy가 likeCount면 좋아요 수 내림차순으로 조회한다")
    void sortBy가_likeCount면_좋아요_수_내림차순으로_조회한다() {
      // given
      Feed low = createAndSaveFeed("좋아요 적음");
      Feed high = createAndSaveFeed("좋아요 많음");
      Feed mid = createAndSaveFeed("좋아요 중간");
      testEntityManager.flush();

      setLikeCount(low.getId(), 1L);
      setLikeCount(high.getId(), 100L);
      setLikeCount(mid.getId(), 50L);
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.LIKE_COUNT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly("좋아요 많음", "좋아요 중간", "좋아요 적음");
    }

    @Test
    @DisplayName("sortDirection이 ASCENDING이면 오래된 순으로 조회한다")
    void sortDirection이_ASCENDING이면_오래된_순으로_조회한다() {
      // given
      Feed first = createAndSaveFeed("첫번째");
      Feed second = createAndSaveFeed("두번째");
      Feed third = createAndSaveFeed("세번째");
      testEntityManager.flush();

      setCreatedAt(first.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(second.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(third.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.ASCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly("첫번째", "두번째", "세번째");
    }

    @Test
    @DisplayName("likeCount 정렬에서 커서 이후의 피드만 조회한다")
    void likeCount_정렬에서_커서_이후의_피드만_조회한다() {
      // given
      Feed a = createAndSaveFeed("A");
      Feed b = createAndSaveFeed("B");
      Feed c = createAndSaveFeed("C");
      testEntityManager.flush();

      setLikeCount(a.getId(), 100L);
      setLikeCount(b.getId(), 50L);
      setLikeCount(c.getId(), 10L);
      testEntityManager.clear();

      // likeCount DESC 순서: A(100) → B(50) → C(10)
      FeedListParams params = new FeedListParams(
          "50", b.getId(), 10,
          FeedSortBy.LIKE_COUNT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly("C");
    }

    @Test
    @DisplayName("createdAt 오름차순에서 커서 이후의 피드만 조회한다")
    void createdAt_오름차순에서_커서_이후의_피드만_조회한다() {
      // given
      Feed first = createAndSaveFeed("첫번째");
      Feed second = createAndSaveFeed("두번째");
      Feed third = createAndSaveFeed("세번째");
      testEntityManager.flush();

      setCreatedAt(first.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(second.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(third.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          Instant.parse("2026-07-28T00:00:01Z").toString(), first.getId(), 10,
          FeedSortBy.CREATED_AT, SortDirection.ASCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly("두번째", "세번째");
    }

    @Test
    @DisplayName("createdAt이 같으면 id 역순으로 tie-break하여 조회한다")
    void createdAt이_같으면_id_역순으로_tie_break하여_조회한다() {
      // given
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Feed a = createAndSaveFeed("A");
      Feed b = createAndSaveFeed("B");
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getId)
          .containsExactlyInAnyOrder(a.getId(), b.getId());
      assertThat(result.data().get(0).getId().toString())
          .isGreaterThan(result.data().get(1).getId().toString());
    }

    @Test
    @DisplayName("createdAt 동률에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void createdAt_동률에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Feed a = createAndSaveFeed("A");
      Feed b = createAndSaveFeed("B");
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      FeedListParams firstPage = new FeedListParams(
          null, null, 1,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      // when: 첫 페이지 조회
      CursorPageResponse<Feed> first = feedRepository.findFeeds(firstPage);

      // then
      assertThat(first.data()).hasSize(1);
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.nextIdAfter()).isNotNull();

      UUID firstId = first.data().get(0).getId();

      // when: 두 번째 페이지 조회
      FeedListParams secondPage = new FeedListParams(
          first.nextCursor(), first.nextIdAfter(), 1,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);
      CursorPageResponse<Feed> second = feedRepository.findFeeds(secondPage);

      // then
      assertThat(second.data()).hasSize(1);
      UUID secondId = second.data().get(0).getId();
      assertThat(secondId).isNotEqualTo(firstId);
      assertThat(List.of(firstId, secondId))
          .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 목록에서 제외한다")
    void 소프트_삭제된_피드는_목록에서_제외한다() {
      // given
      Feed active = createAndSaveFeed("살아있는 피드");
      Feed deleted = createAndSaveFeed("삭제된 피드");
      deleted.delete();
      testEntityManager.flush();
      testEntityManager.clear();

      FeedListParams params = new FeedListParams(
          null, null, 10,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      // when
      CursorPageResponse<Feed> result = feedRepository.findFeeds(params);

      // then
      assertThat(result.data())
          .extracting(Feed::getContent)
          .containsExactly(active.getContent());
      assertThat(result.totalCount()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("날씨 스냅샷 저장")
  class WeatherSnapshotPersistence {

    @Test
    @DisplayName("날씨 enum 스냅샷이 저장 후 조회 시 그대로 유지된다")
    void 날씨_enum_스냅샷이_저장_후_조회_시_그대로_유지된다() {
      // given
      User author = persistUser("작성자");
      Feed feed = feedRepository.save(
          Feed.create(author.getId(), UUID.randomUUID(), "오늘의 착장",
              DUMMY_SNAPSHOT, List.of()));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();

      // then
      assertThat(found.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(found.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(found.getTemperatureCurrent()).isEqualTo(28.0);
      assertThat(found.getTemperatureMin()).isEqualTo(16.0);
      assertThat(found.getTemperatureMax()).isEqualTo(31.0);
    }
  }

  @Nested
  @DisplayName("ootds JSONB 직렬화/역직렬화")
  class OotdsJsonbPersistence {

    @Test
    @DisplayName("OotdSnapshot 리스트가 JSONB로 저장 후 역직렬화돼 그대로 반환된다")
    void OotdSnapshot_리스트가_JSONB로_저장_후_역직렬화돼_그대로_반환된다() {
      // given
      OotdSnapshot ootd1 = new OotdSnapshot(
          UUID.randomUUID(), "패딩", "https://img.url/padding.jpg",
          ClothesType.OUTER, List.of());
      OotdSnapshot ootd2 = new OotdSnapshot(
          UUID.randomUUID(), "청바지", "https://img.url/jeans.jpg",
          ClothesType.BOTTOM, List.of());

      User author = persistUser("작성자");

      Feed feed = feedRepository.save(
          Feed.create(author.getId(), UUID.randomUUID(), "오늘의 착장",
              DUMMY_SNAPSHOT, List.of(ootd1, ootd2)));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();

      // then
      assertThat(found.getOotds()).hasSize(2);
      assertThat(found.getOotds().get(0).name()).isEqualTo("패딩");
      assertThat(found.getOotds().get(0).type()).isEqualTo(ClothesType.OUTER);
      assertThat(found.getOotds().get(1).name()).isEqualTo("청바지");
      assertThat(found.getOotds().get(1).type()).isEqualTo(ClothesType.BOTTOM);
    }

    @Test
    @DisplayName("빈 ootds 리스트가 저장 후 빈 리스트로 반환된다")
    void 빈_ootds_리스트가_저장_후_빈_리스트로_반환된다() {
      // given
      User author = persistUser("작성자");
      Feed feed = feedRepository.save(
          Feed.create(author.getId(), UUID.randomUUID(), "오늘의 착장",
              DUMMY_SNAPSHOT, List.of()));
      testEntityManager.flush();
      testEntityManager.clear();
      
      // when
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();

      // then
      assertThat(found.getOotds()).isEmpty();
    }
  }
}
