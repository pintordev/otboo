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
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FeedLike 통합 테스트")
class FeedLikeIntegrationTest extends IntegrationTestSupport {

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

  /**
   * 동시 요청의 트랜잭션 경합을 재현해야 하므로 상위 클래스의 {@code @Transactional}을 끈다.
   *
   * <p>테스트 트랜잭션 안에서 스레드를 띄우면 {@code @BeforeEach}가 저장한 User·Feed가 아직
   * 커밋되지 않아 다른 스레드에서 보이지 않고, feed_likes의 FK 제약에 먼저 걸린다.
   *
   * <p>롤백이 없으므로 저장한 데이터는 {@code @AfterEach}에서 직접 지운다. 상위 클래스의
   * {@code em}은 트랜잭션이 없어 의미가 없으므로 이 블록에서는 사용하지 않는다.
   */
  @Nested
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("피드 좋아요 동시 요청")
  class ConcurrentLike {

    private static final int CONCURRENCY = 10;

    private User author;
    private User liker;
    private Feed feed;

    @BeforeEach
    void setUp() {
      author = persistUser("동시요청_작성자");
      liker = persistUser("동시요청_좋아요누른사람");
      feed = persistFeed(author.getId());
    }

    @AfterEach
    void tearDown() {
      feedLikeRepository.deleteAllInBatch(
          feedLikeRepository.findAll().stream()
              .filter(fl -> fl.getFeedId().equals(feed.getId()))
              .toList());
      feedRepository.deleteById(feed.getId());
      userRepository.deleteAll(List.of(author, liker));
    }

    @Test
    @DisplayName("동일한 좋아요를 동시에 요청해도 예외 없이 카운트가 1이 된다")
    void 동일한_좋아요를_동시에_요청해도_예외_없이_카운트가_1이_된다() throws Exception {
      // given
      ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
      CountDownLatch ready = new CountDownLatch(CONCURRENCY);
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(CONCURRENCY);
      List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

      // when
      for (int i = 0; i < CONCURRENCY; i++) {
        executor.submit(() -> {
          ready.countDown();
          try {
            start.await();
            feedService.like(feed.getId(), liker.getId());
          } catch (Throwable t) {
            failures.add(t);
          } finally {
            done.countDown();
          }
        });
      }
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      } finally {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      }

      // then
      assertThat(failures)
          .withFailMessage("동시 요청 %d건 중 %d건 실패: %s",
              CONCURRENCY, failures.size(), failures)
          .isEmpty();
      assertThat(feedRepository.findById(feed.getId()).orElseThrow().getLikeCount())
          .isEqualTo(1L);
    }
  }
}
