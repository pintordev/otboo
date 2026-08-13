package com.sprint.mission.otboo.domain.social.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("FeedLikeRepository")
class FeedLikeRepositoryTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @Autowired
  private FeedLikeRepository feedLikeRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private User persistUser(String name) {
    return testEntityManager.persist(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  private Feed persistFeed(UUID authorId) {
    return feedRepository.save(
        Feed.create(authorId, UUID.randomUUID(), "테스트 피드", DUMMY_SNAPSHOT, List.of()));
  }

  @Nested
  @DisplayName("existsByFeedIdAndUserId")
  class ExistsByFeedIdAndUserId {

    @Test
    @DisplayName("해당 사용자의 좋아요가 있으면 true를 반환한다")
    void 해당_사용자의_좋아요가_있으면_true를_반환한다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      feedLikeRepository.save(FeedLike.create(feed.getId(), liker.getId()));

      // when
      boolean exists = feedLikeRepository.existsByFeedIdAndUserId(feed.getId(), liker.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 좋아요만 있으면 false를 반환한다")
    void 다른_사용자의_좋아요만_있으면_false를_반환한다() {
      // given
      User author = persistUser("작성자");
      User other = persistUser("다른유저");
      User me = persistUser("나");
      Feed feed = persistFeed(author.getId());
      feedLikeRepository.save(FeedLike.create(feed.getId(), other.getId()));

      // when
      boolean exists = feedLikeRepository.existsByFeedIdAndUserId(feed.getId(), me.getId());

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("deleteByFeedIdAndUserId")
  class DeleteByFeedIdAndUserId {

    @Test
    @DisplayName("좋아요가 있으면 삭제하고 삭제된 행 수 1을 반환한다")
    void 좋아요가_있으면_삭제하고_삭제된_행_수_1을_반환한다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      feedLikeRepository.save(FeedLike.create(feed.getId(), liker.getId()));

      // when
      long deleted = feedLikeRepository.deleteByFeedIdAndUserId(feed.getId(), liker.getId());

      // then
      assertThat(deleted).isEqualTo(1L);
      assertThat(feedLikeRepository.existsByFeedIdAndUserId(feed.getId(), liker.getId())).isFalse();
    }

    @Test
    @DisplayName("좋아요가 없으면 아무것도 삭제하지 않고 0을 반환한다")
    void 좋아요가_없으면_아무것도_삭제하지_않고_0을_반환한다() {
      // given — 저장 없음

      // when
      long deleted = feedLikeRepository.deleteByFeedIdAndUserId(UUID.randomUUID(),
          UUID.randomUUID());

      // then
      assertThat(deleted).isEqualTo(0L);
    }
  }

  @Nested
  @DisplayName("복합 유니크 제약")
  class UniqueConstraint {

    @Test
    @DisplayName("같은 피드에 같은 사용자가 중복 좋아요하면 uq_feed_likes_feed_id_user_id 위반이 발생한다")
    void 같은_피드에_같은_사용자가_중복_좋아요하면_uq_feed_likes_feed_id_user_id_위반이_발생한다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      testEntityManager.flush();

      feedLikeRepository.saveAndFlush(FeedLike.create(feed.getId(), liker.getId()));

      // when & then
      assertThatThrownBy(
          () -> feedLikeRepository.saveAndFlush(FeedLike.create(feed.getId(), liker.getId())))
          .isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(e -> {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(ConstraintViolationException.class);
            assertThat(((ConstraintViolationException) cause).getConstraintName())
                .isEqualToIgnoringCase("uq_feed_likes_feed_id_user_id");
          });
    }
  }

  @Nested
  @DisplayName("findLikedFeedIds")
  class FindLikedFeedIds {

    @Test
    @DisplayName("주어진 피드들 중 해당 사용자가 좋아요한 피드 ID만 반환한다")
    void 주어진_피드들_중_해당_사용자가_좋아요한_피드_ID만_반환한다() {
      // given
      User author = persistUser("작성자");
      User me = persistUser("나");
      User other = persistUser("다른유저");

      Feed likedFeed = persistFeed(author.getId());
      Feed notLikedFeed = persistFeed(author.getId());
      Feed otherUserFeed = persistFeed(author.getId());

      feedLikeRepository.save(FeedLike.create(likedFeed.getId(), me.getId()));
      feedLikeRepository.save(FeedLike.create(otherUserFeed.getId(), other.getId()));
      feedLikeRepository.saveAndFlush(FeedLike.create(notLikedFeed.getId(), other.getId()));

      // when
      List<UUID> result = feedLikeRepository.findLikedFeedIds(
          me.getId(), List.of(likedFeed.getId(), notLikedFeed.getId(), otherUserFeed.getId()));

      // then
      assertThat(result).containsExactly(likedFeed.getId());
    }
  }
}
