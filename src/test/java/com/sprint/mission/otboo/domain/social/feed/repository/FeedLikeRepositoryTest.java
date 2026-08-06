package com.sprint.mission.otboo.domain.social.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("FeedLikeRepository")
class FeedLikeRepositoryTest {

  @Autowired
  private FeedLikeRepository feedLikeRepository;

  @Nested
  @DisplayName("existsByFeedIdAndUserId")
  class ExistsByFeedIdAndUserId {

    @Test
    @DisplayName("해당 사용자의 좋아요가 있으면 true를 반환한다")
    void 해당_사용자의_좋아요가_있으면_true를_반환한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      feedLikeRepository.save(FeedLike.create(feedId, userId));

      // when
      boolean exists = feedLikeRepository.existsByFeedIdAndUserId(feedId, userId);

      // then
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("해당 사용자의 좋아요가 없으면 false를 반환한다")
    void 해당_사용자의_좋아요가_없으면_false를_반환한다() {
      // given — 같은 피드에 다른 사용자만 좋아요
      UUID feedId = UUID.randomUUID();
      feedLikeRepository.save(FeedLike.create(feedId, UUID.randomUUID()));

      // when
      boolean exists = feedLikeRepository.existsByFeedIdAndUserId(feedId, UUID.randomUUID());

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
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      feedLikeRepository.save(FeedLike.create(feedId, userId));

      // when
      long deleted = feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId);

      // then
      assertThat(deleted).isEqualTo(1L);
      assertThat(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).isFalse();
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
    @DisplayName("같은 피드에 같은 사용자가 중복 좋아요하면 DataIntegrityViolationException이 발생한다")
    void 같은_피드에_같은_사용자가_중복_좋아요하면_DataIntegrityViolationException이_발생한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      feedLikeRepository.saveAndFlush(FeedLike.create(feedId, userId));

      // when & then
      assertThatThrownBy(
          () -> feedLikeRepository.saveAndFlush(FeedLike.create(feedId, userId)))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("findLikedFeedIds")
  class FindLikedFeedIds {

    @Test
    @DisplayName("주어진 피드들 중 해당 사용자가 좋아요한 피드 ID만 반환한다")
    void 주어진_피드들_중_해당_사용자가_좋아요한_피드_ID만_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID likedFeedId = UUID.randomUUID();
      UUID notLikedFeedId = UUID.randomUUID();
      UUID otherUserFeedId = UUID.randomUUID();

      feedLikeRepository.save(FeedLike.create(likedFeedId, userId));
      feedLikeRepository.save(FeedLike.create(otherUserFeedId, UUID.randomUUID())); // 다른 유저
      feedLikeRepository.saveAndFlush(
          FeedLike.create(notLikedFeedId, UUID.randomUUID())); // 또 다른 유저

      // when
      List<UUID> result = feedLikeRepository.findLikedFeedIds(
          userId, List.of(likedFeedId, notLikedFeedId, otherUserFeedId));

      // then
      assertThat(result).containsExactly(likedFeedId);
    }
  }
}