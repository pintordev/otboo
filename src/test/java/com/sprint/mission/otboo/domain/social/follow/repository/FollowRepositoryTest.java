package com.sprint.mission.otboo.domain.social.follow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.social.follow.entity.Follow;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.List;
import java.util.Optional;
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
@DisplayName("FollowRepository")
class FollowRepositoryTest {

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private User persistUser(String name) {
    return testEntityManager.persist(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  @Nested
  @DisplayName("existsByFollowerIdAndFolloweeId")
  class ExistsByFollowerIdAndFolloweeId {

    @Test
    @DisplayName("팔로우가 존재하면 true를 반환한다")
    void 팔로우가_존재하면_true를_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      followRepository.save(Follow.create(followerId, followeeId));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      boolean result = followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("팔로우가 없으면 false를 반환한다")
    void 팔로우가_없으면_false를_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();

      // when
      boolean result = followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("findByFollowerIdAndFolloweeId")
  class FindByFollowerIdAndFolloweeId {

    @Test
    @DisplayName("팔로우가 존재하면 해당 Follow를 반환한다")
    void 팔로우가_존재하면_해당_Follow를_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      Follow saved = followRepository.save(Follow.create(followerId, followeeId));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      Optional<Follow> result =
          followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId);

      // then
      assertThat(result).hasValueSatisfying(follow -> {
        assertThat(follow.getId()).isEqualTo(saved.getId());
        assertThat(follow.getFollowerId()).isEqualTo(followerId);
        assertThat(follow.getFolloweeId()).isEqualTo(followeeId);
      });
    }

    @Test
    @DisplayName("팔로우가 없으면 빈 Optional을 반환한다")
    void 팔로우가_없으면_빈_Optional을_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();

      // when
      Optional<Follow> result =
          followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("unique constraint")
  class UniqueConstraint {

    @Test
    @DisplayName("동일한 follower-followee 조합을 중복 저장하면 uq_follows_follower_id_followee_id 위반이 발생한다")
    void 동일한_follower_followee_조합을_중복_저장하면_uq_follows_follower_id_followee_id_위반이_발생한다() {
      // given
      User follower = persistUser("팔로워");
      User followee = persistUser("팔로위");
      testEntityManager.flush();

      followRepository.saveAndFlush(Follow.create(follower.getId(), followee.getId()));

      // when & then
      assertThatThrownBy(() ->
          followRepository.saveAndFlush(Follow.create(follower.getId(), followee.getId()))
      ).isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(e -> {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(ConstraintViolationException.class);
            assertThat(((ConstraintViolationException) cause).getConstraintName())
                .isEqualToIgnoringCase("uq_follows_follower_id_followee_id");
          });
    }
  }

  @Nested
  @DisplayName("count")
  class Count {

    @Test
    @DisplayName("followeeId로 팔로워 수를 센다")
    void followeeId로_팔로워_수를_센다() {
      // given
      UUID target = UUID.randomUUID();
      followRepository.save(Follow.create(UUID.randomUUID(), target));
      followRepository.save(Follow.create(UUID.randomUUID(), target));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      long count = followRepository.countByFolloweeId(target);

      // then
      assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("followerId로 팔로잉 수를 센다")
    void followerId로_팔로잉_수를_센다() {
      // given
      UUID user = UUID.randomUUID();
      followRepository.save(Follow.create(user, UUID.randomUUID()));
      followRepository.save(Follow.create(user, UUID.randomUUID()));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      long count = followRepository.countByFollowerId(user);

      // then
      assertThat(count).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("findFollowerIds")
  class FindFollowerIds {

    @Test
    @DisplayName("특정 사용자를 팔로우하는 사용자들의 id를 반환한다")
    void 특정_사용자를_팔로우하는_사용자들의_id를_반환한다() {
      // given
      UUID followeeId = UUID.randomUUID();
      UUID follower1 = UUID.randomUUID();
      UUID follower2 = UUID.randomUUID();
      followRepository.save(Follow.create(follower1, followeeId));
      followRepository.save(Follow.create(follower2, followeeId));
      followRepository.save(Follow.create(UUID.randomUUID(), UUID.randomUUID()));  // 다른 관계
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<UUID> result = followRepository.findFollowerIds(followeeId);

      // then
      assertThat(result).containsExactlyInAnyOrder(follower1, follower2);
    }

    @Test
    @DisplayName("팔로워가 없으면 빈 목록을 반환한다")
    void 팔로워가_없으면_빈_목록을_반환한다() {
      // when
      List<UUID> result = followRepository.findFollowerIds(UUID.randomUUID());

      // then
      assertThat(result).isEmpty();
    }
  }
}
