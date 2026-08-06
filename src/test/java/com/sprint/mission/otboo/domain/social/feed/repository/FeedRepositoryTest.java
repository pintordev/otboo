package com.sprint.mission.otboo.domain.social.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

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

  private Feed createAndSaveFeed(String content) {
    return feedRepository.save(
        Feed.create(UUID.randomUUID(), UUID.randomUUID(), content, DUMMY_SNAPSHOT, List.of()));
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
    @DisplayName("좋아요 카운트를 1 증가시킨다")
    void 좋아요_카운트를_1_증가시킨다() {
      // given
      Feed feed = createAndSaveFeed("내용");

      // when
      feedRepository.incrementLikeCount(feed.getId());

      // then
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("decrementLikeCount")
  class DecrementLikeCount {

    @Test
    @DisplayName("좋아요 카운트를 1 감소시킨다")
    void 좋아요_카운트를_1_감소시킨다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      setLikeCount(feed.getId(), 2L);

      // when
      feedRepository.decrementLikeCount(feed.getId());

      // then
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요 카운트가 0이면 감소시켜도 0을 유지한다")
    void 좋아요_카운트가_0이면_감소시켜도_0을_유지한다() {
      // given
      Feed feed = createAndSaveFeed("내용");
      // like_count는 생성 시 0

      // when
      feedRepository.decrementLikeCount(feed.getId());

      // then
      Feed found = feedRepository.findById(feed.getId()).orElseThrow();
      assertThat(found.getLikeCount()).isEqualTo(0L);
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
      UUID authorId = UUID.randomUUID();
      Feed feed = feedRepository.save(
          Feed.create(authorId, UUID.randomUUID(), "내용", DUMMY_SNAPSHOT, List.of()));

      // when
      Optional<UUID> result = feedRepository.findAuthorId(feed.getId());

      // then
      assertThat(result).contains(authorId);
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
      Feed feed = feedRepository.save(
          Feed.create(UUID.randomUUID(), UUID.randomUUID(), "내용", DUMMY_SNAPSHOT, List.of()));
      setDeletedAt(feed.getId(), Instant.now());

      // when
      Optional<UUID> result = feedRepository.findAuthorId(feed.getId());

      // then
      assertThat(result).isEmpty();
    }
  }
}