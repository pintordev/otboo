package com.sprint.mission.otboo.domain.social.follow.repository.querydsl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowerListParams;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowingListParams;
import com.sprint.mission.otboo.domain.social.follow.entity.Follow;
import com.sprint.mission.otboo.domain.social.follow.repository.FollowRepository;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
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
@DisplayName("FollowCustomRepository")
class FollowCustomRepositoryImplTest {

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private User persistUser(String name) {
    return testEntityManager.persist(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  private void setCreatedAt(UUID followId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update follows set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", followId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("findFollowings")
  class FindFollowings {

    @Test
    @DisplayName("특정 follower의 팔로잉만 limit + 1개까지 조회한다")
    void 특정_follower의_팔로잉만_limit_플러스_1개까지_조회한다() {
      // given
      User follower = persistUser("팔로워");
      for (int i = 0; i < 3; i++) {
        User followee = persistUser("팔로위" + i);
        followRepository.save(Follow.create(follower.getId(), followee.getId()));
      }
      followRepository.save(Follow.create(
          persistUser("다른팔로워").getId(), persistUser("다른팔로위").getId())); // 다른 follower
      testEntityManager.flush();
      testEntityManager.clear();

      FollowingListParams params = new FollowingListParams(follower.getId(), null, null, 2, null);

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowings(params);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3);
      assertThat(result.data())
          .allSatisfy(f -> assertThat(f.getFollowerId()).isEqualTo(follower.getId()));
    }

    @Test
    @DisplayName("커서 이후의 팔로잉만 조회한다")
    void 커서_이후의_팔로잉만_조회한다() {
      // given
      User follower = persistUser("팔로워");
      User f1 = persistUser("팔로위1");
      User f2 = persistUser("팔로위2");
      User f3 = persistUser("팔로위3");
      Follow follow1 = followRepository.save(Follow.create(follower.getId(), f1.getId()));
      Follow follow2 = followRepository.save(Follow.create(follower.getId(), f2.getId()));
      Follow third = followRepository.save(Follow.create(follower.getId(), f3.getId()));
      testEntityManager.flush();

      // 명시적 시간 부여 → tie-break 방지
      setCreatedAt(follow1.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(follow2.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(third.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      // createdAt DESC: f3(t3) → f2(t2) → f1(t1), 커서 = third(t3)
      FollowingListParams params = new FollowingListParams(
          follower.getId(), Instant.parse("2026-07-28T00:00:03Z").toString(),
          third.getId(), 10, null);

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowings(params);

      // then
      assertThat(result.data())
          .extracting(Follow::getFolloweeId)
          .containsExactly(f2.getId(), f1.getId());
    }

    @Test
    @DisplayName("nameLike가 주어지면 팔로위 이름에 해당 키워드를 포함한 팔로잉만 조회한다")
    void nameLike가_주어지면_팔로위_이름에_해당_키워드를_포함한_팔로잉만_조회한다() {
      // given
      User follower = persistUser("팔로워");
      User woody = persistUser("우디");
      User buzz = persistUser("버즈");
      User woodyFriend = persistUser("우디친구");
      followRepository.save(Follow.create(follower.getId(), woody.getId()));
      followRepository.save(Follow.create(follower.getId(), buzz.getId()));
      followRepository.save(Follow.create(follower.getId(), woodyFriend.getId()));
      testEntityManager.flush();
      testEntityManager.clear();

      FollowingListParams params = new FollowingListParams(follower.getId(), null, null, 10, "우디");

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowings(params);

      // then
      assertThat(result.data())
          .extracting(Follow::getFolloweeId)
          .containsExactlyInAnyOrder(woody.getId(), woodyFriend.getId());
      assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("createdAt이 같으면 id 역순으로 tie-break하여 조회한다")
    void createdAt이_같으면_id_역순으로_tie_break하여_조회한다() {
      // given
      User follower = persistUser("팔로워");
      User fa = persistUser("팔로위A");
      User fb = persistUser("팔로위B");
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Follow a = followRepository.save(Follow.create(follower.getId(), fa.getId()));
      Follow b = followRepository.save(Follow.create(follower.getId(), fb.getId()));
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      FollowingListParams params = new FollowingListParams(follower.getId(), null, null, 10, null);

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowings(params);

      // then
      // 같은 createdAt이므로 follow id DESC로 tie-break되어 두 건 모두 조회됨
      assertThat(result.data())
          .extracting(Follow::getId)
          .containsExactlyInAnyOrder(a.getId(), b.getId());
      assertThat(result.data().get(0).getId().toString())
          .isGreaterThan(result.data().get(1).getId().toString());
    }

    @Test
    @DisplayName("createdAt 동률에서 커서로 다음 페이지를 조회하면 나머지가 중복·누락 없이 조회된다")
    void createdAt_동률에서_커서로_다음_페이지를_조회하면_나머지가_중복_누락_없이_조회된다() {
      // given
      User follower = persistUser("팔로워");
      User fa = persistUser("팔로위A");
      User fb = persistUser("팔로위B");
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Follow a = followRepository.save(Follow.create(follower.getId(), fa.getId()));
      Follow b = followRepository.save(Follow.create(follower.getId(), fb.getId()));
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      FollowingListParams firstPage =
          new FollowingListParams(follower.getId(), null, null, 1, null);

      // when: 첫 페이지 조회
      CursorPageResponse<Follow> first = followRepository.findFollowings(firstPage);

      // then
      assertThat(first.data()).hasSize(1);
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.nextIdAfter()).isNotNull();

      UUID firstId = first.data().get(0).getId();

      // when: 커서로 다음 페이지 조회
      FollowingListParams secondPage = new FollowingListParams(
          follower.getId(), first.nextCursor(), first.nextIdAfter(), 1, null);
      CursorPageResponse<Follow> second = followRepository.findFollowings(secondPage);

      // then
      assertThat(second.data()).hasSize(1);
      UUID secondId = second.data().get(0).getId();
      assertThat(secondId).isNotEqualTo(firstId);
      assertThat(List.of(firstId, secondId)).containsExactlyInAnyOrder(a.getId(), b.getId());
    }
  }

  @Nested
  @DisplayName("findFollowers")
  class FindFollowers {

    @Test
    @DisplayName("특정 followee의 팔로워만 limit + 1개까지 조회한다")
    void 특정_followee의_팔로워만_limit_플러스_1개까지_조회한다() {
      // given
      User followee = persistUser("팔로위");
      for (int i = 0; i < 3; i++) {
        User f = persistUser("팔로워" + i);
        followRepository.save(Follow.create(f.getId(), followee.getId()));
      }
      followRepository.save(Follow.create(
          persistUser("다른팔로워").getId(), persistUser("다른팔로위").getId())); // 다른 followee
      testEntityManager.flush();
      testEntityManager.clear();

      FollowerListParams params = new FollowerListParams(followee.getId(), null, null, 2, null);

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowers(params);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3);
      assertThat(result.data())
          .allSatisfy(f -> assertThat(f.getFolloweeId()).isEqualTo(followee.getId()));
    }

    @Test
    @DisplayName("nameLike가 주어지면 팔로워 이름에 해당 키워드를 포함한 팔로워만 조회한다")
    void nameLike가_주어지면_팔로워_이름에_해당_키워드를_포함한_팔로워만_조회한다() {
      // given
      User followee = persistUser("팔로위");
      User woody = persistUser("우디");
      User buzz = persistUser("버즈");
      User woodyFriend = persistUser("우디친구");
      followRepository.save(Follow.create(woody.getId(), followee.getId()));
      followRepository.save(Follow.create(buzz.getId(), followee.getId()));
      followRepository.save(Follow.create(woodyFriend.getId(), followee.getId()));
      testEntityManager.flush();
      testEntityManager.clear();

      FollowerListParams params = new FollowerListParams(followee.getId(), null, null, 10, "우디");

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowers(params);

      // then
      assertThat(result.data())
          .extracting(Follow::getFollowerId)
          .containsExactlyInAnyOrder(woody.getId(), woodyFriend.getId());
      assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("커서 이후의 팔로워만 조회한다")
    void 커서_이후의_팔로워만_조회한다() {
      // given
      User followee = persistUser("팔로위");
      User f1 = persistUser("팔로워1");
      User f2 = persistUser("팔로워2");
      User f3 = persistUser("팔로워3");
      Follow follow1 = followRepository.save(Follow.create(f1.getId(), followee.getId()));
      Follow follow2 = followRepository.save(Follow.create(f2.getId(), followee.getId()));
      Follow third = followRepository.save(Follow.create(f3.getId(), followee.getId()));
      testEntityManager.flush();

      setCreatedAt(follow1.getId(), Instant.parse("2026-07-28T00:00:01Z"));
      setCreatedAt(follow2.getId(), Instant.parse("2026-07-28T00:00:02Z"));
      setCreatedAt(third.getId(), Instant.parse("2026-07-28T00:00:03Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      // createdAt DESC: f3(t3) → f2(t2) → f1(t1), 커서 = third(t3)
      FollowerListParams params = new FollowerListParams(
          followee.getId(), Instant.parse("2026-07-28T00:00:03Z").toString(),
          third.getId(), 10, null);

      // when
      CursorPageResponse<Follow> result = followRepository.findFollowers(params);

      // then
      assertThat(result.data())
          .extracting(Follow::getFollowerId)
          .containsExactly(f2.getId(), f1.getId());
    }
  }
}
