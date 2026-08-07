package com.sprint.mission.otboo.domain.social.common.repository.querydsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.impl.UserSummaryQueryRepositoryImpl;
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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class, UserSummaryQueryRepositoryImpl.class})
@DisplayName("UserSummaryQueryRepository")
class UserSummaryQueryRepositoryImplTest {

  @Autowired
  private UserSummaryQueryRepository userSummaryQueryRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("findByUserId")
  class FindByUserId {

    @Test
    @DisplayName("유저 ID로 name과 profileImageUrl을 채운 UserSummary를 반환한다")
    void 유저_ID로_name과_profileImageUrl을_채운_UserSummary를_반환한다() {
      // given
      User user = User.create("otboo", "otboo@test.com", "encoded-password");
      testEntityManager.persist(user);
      Profile profile = Profile.create(user);
      testEntityManager.persist(profile);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      UserSummary result = userSummaryQueryRepository.findByUserId(user.getId());

      // then
      assertThat(result).isNotNull();
      assertThat(result.userId()).isEqualTo(user.getId());
      assertThat(result.name()).isEqualTo("otboo");
      assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지가 있으면 profileImageUrl까지 채운 UserSummary를 반환한다")
    void 프로필_이미지가_있으면_profileImageUrl까지_채운_UserSummary를_반환한다() {
      // given
      User user = User.create("otboo", "otboo@test.com", "encoded-password");
      testEntityManager.persist(user);
      Profile profile = Profile.create(user);
      ReflectionTestUtils.setField(profile, "profileImageUrl", "https://img.url/otboo.png");
      testEntityManager.persist(profile);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      UserSummary result = userSummaryQueryRepository.findByUserId(user.getId());

      // then
      assertThat(result.userId()).isEqualTo(user.getId());
      assertThat(result.name()).isEqualTo("otboo");
      assertThat(result.profileImageUrl()).isEqualTo("https://img.url/otboo.png");
    }

    @Test
    @DisplayName("존재하지 않는 userId면 UserNotFoundException을 던진다")
    void 존재하지_않는_userId면_UserNotFoundException을_던진다() {
      // given
      UUID unknownId = UUID.randomUUID();

      // when & then
      assertThatThrownBy(() -> userSummaryQueryRepository.findByUserId(unknownId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("existsByUserId")
  class ExistsByUserId {

    @Test
    @DisplayName("존재하는 userId면 true를 반환한다")
    void 존재하는_userId면_true를_반환한다() {
      // given
      User user = User.create("otboo", "otboo@test.com", "encoded-password");
      testEntityManager.persist(user);
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      boolean result = userSummaryQueryRepository.existsByUserId(user.getId());

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 userId면 false를 반환한다")
    void 존재하지_않는_userId면_false를_반환한다() {
      // given
      UUID unknownId = UUID.randomUUID();

      // when
      boolean result = userSummaryQueryRepository.existsByUserId(unknownId);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("findByUserIds")
  class FindByUserIds {

    @Test
    @DisplayName("여러 userId로 조회하면 해당 UserSummary들을 반환한다")
    void 여러_userId로_조회하면_해당_UserSummary들을_반환한다() {
      // given
      User user1 = testEntityManager.persist(User.create("우디", "woody@otboo.io", "password"));
      User user2 = testEntityManager.persist(User.create("버즈", "buzz@otboo.io", "password"));
      testEntityManager.persist(Profile.create(user1));
      testEntityManager.persist(Profile.create(user2));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<UserSummary> result = userSummaryQueryRepository.findByUserIds(
          List.of(user1.getId(), user2.getId()));

      // then
      assertThat(result)
          .extracting(UserSummary::userId)
          .containsExactlyInAnyOrder(user1.getId(), user2.getId());
    }
  }


}