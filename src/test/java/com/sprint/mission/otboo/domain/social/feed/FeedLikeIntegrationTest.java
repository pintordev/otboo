package com.sprint.mission.otboo.domain.social.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.service.FeedService;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FeedLike 통합 테스트")
class FeedLikeIntegrationTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @Autowired
  private FeedService feedService;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private FeedLikeRepository feedLikeRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager em;

  private User persistUser(String name) {
    return userRepository.save(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  private Feed persistFeed(UUID authorId) {
    return feedRepository.save(
        Feed.create(authorId, UUID.randomUUID(), "테스트 피드", DUMMY_SNAPSHOT, List.of()));
  }

  @Nested
  @DisplayName("피드 좋아요")
  class Like {

    @Test
    @DisplayName("좋아요하면 DB에 저장되고 카운트가 증가한다")
    void 좋아요하면_DB에_저장되고_카운트가_증가한다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      em.flush();

      // when
      feedService.like(feed.getId(), liker.getId());

      em.flush();
      em.clear();

      // then
      assertThat(feedLikeRepository.existsByFeedIdAndUserId(
          feed.getId(), liker.getId())).isTrue();
      assertThat(feedRepository.findById(feed.getId()).orElseThrow().getLikeCount())
          .isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 좋아요한 상태면 중복 저장하지 않고 카운트도 증가하지 않는다")
    void 이미_좋아요한_상태면_중복_저장하지_않고_카운트도_증가하지_않는다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      em.flush();

      feedService.like(feed.getId(), liker.getId());
      em.flush();
      em.clear();

      // when
      feedService.like(feed.getId(), liker.getId());
      em.flush();
      em.clear();

      // then
      assertThat(feedLikeRepository.count()).isEqualTo(1);
      assertThat(feedRepository.findById(feed.getId()).orElseThrow().getLikeCount())
          .isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("피드 좋아요 취소")
  class Unlike {

    @Test
    @DisplayName("좋아요를 취소하면 삭제되고 카운트가 감소한다")
    void 좋아요를_취소하면_삭제되고_카운트가_감소한다() {
      // given
      User author = persistUser("작성자");
      User liker = persistUser("좋아요누른사람");
      Feed feed = persistFeed(author.getId());
      em.flush();

      feedService.like(feed.getId(), liker.getId());
      em.flush();
      em.clear();

      // when
      feedService.unlike(feed.getId(), liker.getId());
      em.flush();
      em.clear();

      // then
      assertThat(feedLikeRepository.existsByFeedIdAndUserId(
          feed.getId(), liker.getId())).isFalse();
      assertThat(feedRepository.findById(feed.getId()).orElseThrow().getLikeCount())
          .isZero();
    }
  }
}
