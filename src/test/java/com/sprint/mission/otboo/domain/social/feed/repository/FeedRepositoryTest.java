package com.sprint.mission.otboo.domain.social.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
@DisplayName("FeedRepository")
class FeedRepositoryTest {

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

  private Feed createAndSaveFeed(String content) {
    User author = persistUser("작성자");
    return feedRepository.save(
        Feed.create(author.getId(), UUID.randomUUID(), content, DUMMY_SNAPSHOT, List.of()));
  }

  private void setLikeCount(UUID feedId, long count) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update feeds set like_count = :count where id = :id")
        .setParameter("count", count)
        .setParameter("id", feedId)
        .executeUpdate();
  }

  private void setDeletedAt(UUID feedId, Instant deletedAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update feeds set deleted_at = :deletedAt where id = :id")
        .setParameter("deletedAt", deletedAt)
        .setParameter("id", feedId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("incrementLikeCount")
  class IncrementLikeCount {

    @Test
    @DisplayName("좋아요 카운트를 1 증가시키고 수정 행 수 1을 반환한다")
    void 좋아요_카운트를_1_증가시키고_수정_행_수_1을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");

      // when
      int updated = feedRepository.incrementLikeCount(feed.getId());

      // then
      assertThat(updated).isEqualTo(1);
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 카운트가 변경되지 않고 0을 반환한다")
    void 소프트_삭제된_피드는_카운트가_변경되지_않고_0을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setDeletedAt(feed.getId(), Instant.now());

      // when
      int updated = feedRepository.incrementLikeCount(feed.getId());

      // then
      assertThat(updated).isZero();
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isZero();
    }
  }

  @Nested
  @DisplayName("decrementLikeCount")
  class DecrementLikeCount {

    @Test
    @DisplayName("좋아요 카운트를 1 감소시키고 수정 행 수 1을 반환한다")
    void 좋아요_카운트를_1_감소시키고_수정_행_수_1을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setLikeCount(feed.getId(), 2L);

      // when
      int updated = feedRepository.decrementLikeCount(feed.getId());

      // then
      assertThat(updated).isEqualTo(1);
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요 카운트가 0이면 감소시키지 않고 0을 반환한다")
    void 좋아요_카운트가_0이면_감소시키지_않고_0을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      // like_count는 생성 시 0

      // when
      int updated = feedRepository.decrementLikeCount(feed.getId());

      // then
      assertThat(updated).isZero();
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 카운트가 변경되지 않고 0을 반환한다")
    void 소프트_삭제된_피드는_카운트가_변경되지_않고_0을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setLikeCount(feed.getId(), 2L);
      setDeletedAt(feed.getId(), Instant.now());

      // when
      int updated = feedRepository.decrementLikeCount(feed.getId());

      // then
      assertThat(updated).isZero();
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(2L);
    }
  }

  @Nested
  @DisplayName("existsByIdAndSoftDeletable_DeletedAtIsNull")
  class ExistsByIdAndSoftDeletableDeletedAtIsNull {

    @Test
    @DisplayName("삭제되지 않은 피드가 있으면 true를 반환한다")
    void 삭제되지_않은_피드가_있으면_true를_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");

      // when
      boolean exists = feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feed.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("피드가 없으면 false를 반환한다")
    void 피드가_없으면_false를_반환한다() {
      // when
      boolean exists = feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(UUID.randomUUID());

      // then
      assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 false를 반환한다")
    void 소프트_삭제된_피드는_false를_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setDeletedAt(feed.getId(), Instant.now());

      // when
      boolean exists = feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feed.getId());

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("findAuthorId")
  class FindAuthorId {

    @Test
    @DisplayName("피드의 작성자 ID를 반환한다")
    void 피드의_작성자_ID를_반환한다() {
      // given
      User author = persistUser("작성자");
      Feed feed = feedRepository.save(
          Feed.create(author.getId(), UUID.randomUUID(), "내용", DUMMY_SNAPSHOT, List.of()));

      // when
      Optional<UUID> result = feedRepository.findAuthorId(feed.getId());

      // then
      assertThat(result).contains(author.getId());
    }

    @Test
    @DisplayName("피드가 없으면 빈 Optional을 반환한다")
    void 피드가_없으면_빈_Optional을_반환한다() {
      // when
      Optional<UUID> result = feedRepository.findAuthorId(UUID.randomUUID());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 빈 Optional을 반환한다")
    void 소프트_삭제된_피드는_빈_Optional을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setDeletedAt(feed.getId(), Instant.now());

      // when
      Optional<UUID> result = feedRepository.findAuthorId(feed.getId());

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("incrementCommentCount")
  class IncrementCommentCount {

    @Test
    @DisplayName("댓글 카운트를 1 증가시키고 수정 행 수 1을 반환한다")
    void 댓글_카운트를_1_증가시키고_수정_행_수_1을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");

      // when
      int updated = feedRepository.incrementCommentCount(feed.getId());

      // then
      assertThat(updated).isEqualTo(1);
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 카운트가 변경되지 않고 0을 반환한다")
    void 소프트_삭제된_피드는_카운트가_변경되지_않고_0을_반환한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setDeletedAt(feed.getId(), Instant.now());

      // when
      int updated = feedRepository.incrementCommentCount(feed.getId());

      // then
      assertThat(updated).isZero();
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getCommentCount()).isZero();
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

    @Test
    @DisplayName("어제 비교 데이터가 없어 temperatureCompared가 null이어도 저장된다")
    void 어제_비교_데이터가_없어_temperatureCompared가_null이어도_저장된다() {
      // given
      WeatherSnapshot snapshot = new WeatherSnapshot(
          SkyStatus.CLEAR, PrecipitationType.NONE,
          0.0, 0.0, 28.0, null, 16.0, 31.0);
      User author = persistUser("작성자");

      // when
      Feed feed = feedRepository.save(
          Feed.create(author.getId(), UUID.randomUUID(), "비교 불가 날씨", snapshot, List.of()));
      testEntityManager.flush();
      testEntityManager.clear();

      // then
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getTemperatureCompared()).isNull();
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

  @Nested
  @DisplayName("findAllActiveByIds")
  class FindAllActiveByIds {

    @Test
    @DisplayName("소프트 삭제되지 않은 피드만 반환한다")
    void 소프트_삭제되지_않은_피드만_반환한다() {
      // given
      Feed active = createAndSaveFeed("살아있는 피드");
      Feed deleted = createAndSaveFeed("삭제된 피드");
      deleted.delete();
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Feed> result = feedRepository.findAllActiveByIds(
          List.of(active.getId(), deleted.getId()));

      // then
      assertThat(result)
          .extracting(Feed::getContent)
          .containsExactly("살아있는 피드");
    }
  }
}
