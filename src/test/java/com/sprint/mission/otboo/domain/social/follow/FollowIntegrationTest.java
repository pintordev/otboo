package com.sprint.mission.otboo.domain.social.follow;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowCreateRequest;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowDto;
import com.sprint.mission.otboo.domain.social.follow.repository.FollowRepository;
import com.sprint.mission.otboo.domain.social.follow.service.FollowService;
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
@DisplayName("Follow 통합 테스트")
class FollowIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private FollowService followService;

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager em;

  private User persistUser(String name) {
    return userRepository.save(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  @Nested
  @DisplayName("팔로우 생성")
  class CreateFollow {

    @Test
    @DisplayName("팔로우를 생성하면 DB에 저장되고 FollowDto를 반환한다")
    void 팔로우를_생성하면_DB에_저장되고_FollowDto를_반환한다() {
      // given
      User follower = persistUser("팔로워");
      User followee = persistUser("팔로위");
      em.flush();

      FollowCreateRequest request =
          new FollowCreateRequest(followee.getId(), follower.getId());

      // when
      FollowDto result = followService.create(request, follower.getId());

      em.flush();
      em.clear();

      // then
      assertThat(result.id()).isNotNull();
      assertThat(result.follower().userId()).isEqualTo(follower.getId());
      assertThat(result.followee().userId()).isEqualTo(followee.getId());

      assertThat(followRepository.findByFollowerIdAndFolloweeId(
          follower.getId(), followee.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 팔로우 중이면 새로 저장하지 않고 기존 관계를 반환한다")
    void 이미_팔로우_중이면_새로_저장하지_않고_기존_관계를_반환한다() {
      // given
      User follower = persistUser("팔로워");
      User followee = persistUser("팔로위");
      em.flush();

      FollowCreateRequest request =
          new FollowCreateRequest(followee.getId(), follower.getId());
      FollowDto first = followService.create(request, follower.getId());
      em.flush();
      em.clear();

      // when
      FollowDto second = followService.create(request, follower.getId());
      em.flush();
      em.clear();

      // then
      assertThat(second.id()).isEqualTo(first.id());
      assertThat(followRepository.count()).isEqualTo(1);
    }
  }

  /**
   * 동시 요청의 트랜잭션 경합을 재현해야 하므로 상위 클래스의 {@code @Transactional}을 끈다.
   *
   * <p>테스트 트랜잭션 안에서 스레드를 띄우면 {@code @BeforeEach}가 저장한 User가 아직 커밋되지 않아
   * 다른 스레드에서 보이지 않고, follows의 FK 제약에 먼저 걸려 UQ 경합까지 도달하지 못한다.
   *
   * <p>롤백이 없으므로 저장한 데이터는 {@code @AfterEach}에서 직접 지운다. 상위 클래스의
   * {@code em}은 트랜잭션이 없어 의미가 없으므로 이 블록에서는 사용하지 않는다.
   */
  @Nested
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("팔로우 동시 생성")
  class ConcurrentCreate {

    private static final int CONCURRENCY = 10;

    private User concurrentFollower;
    private User concurrentFollowee;

    @BeforeEach
    void setUp() {
      concurrentFollower = persistUser("동시요청_팔로워");
      concurrentFollowee = persistUser("동시요청_팔로위");
    }

    @AfterEach
    void tearDown() {
      followRepository
          .findByFollowerIdAndFolloweeId(concurrentFollower.getId(), concurrentFollowee.getId())
          .ifPresent(followRepository::delete);
      userRepository.deleteAll(List.of(concurrentFollower, concurrentFollowee));
    }

    @Test
    @DisplayName("동일한 팔로우를 동시에 요청해도 예외 없이 1건만 저장된다")
    void 동일한_팔로우를_동시에_요청해도_예외_없이_1건만_저장된다() throws Exception {
      // given
      FollowCreateRequest request =
          new FollowCreateRequest(concurrentFollowee.getId(), concurrentFollower.getId());
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
            followService.create(request, concurrentFollower.getId());
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
      assertThat(followRepository.countByFolloweeId(concurrentFollowee.getId())).isEqualTo(1L);
    }
  }
}
