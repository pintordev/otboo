package com.sprint.mission.otboo.domain.authuser.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
@DisplayName("ProfileRepository")
class ProfileRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProfileRepository profileRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("저장 (save)")
  class Save {

    @Test
    @DisplayName("Profile을 저장하면 User와 동일한 ID를 공유한다 (@MapsId)")
    void Profile을_저장하면_User와_동일한_ID를_공유한다() {
      // given
      User user = userRepository.save(User.create("홍길동", "hong@test.com", "encoded-password"));
      testEntityManager.flush();
      Profile profile = Profile.create(user);

      // when
      Profile savedProfile = profileRepository.save(profile);
      testEntityManager.flush();
      testEntityManager.clear();

      // then
      assertThat(savedProfile.getId()).isEqualTo(user.getId());
      Optional<Profile> found = profileRepository.findById(user.getId());
      assertThat(found).isPresent();
      assertThat(found.get().getTemperatureSensitivity()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("기본 프로필 생성 (Profile.create)")
  class CreateDefaultProfile {

    @Test
    @DisplayName("Profile은 기본 온도 민감도 3, 성별/위치 없음으로 생성된다")
    void Profile은_기본_온도_민감도_3_성별_위치_없음으로_생성된다() {
      // given
      User user = userRepository.save(User.create("홍길동", "hong2@test.com", "encoded-password"));
      testEntityManager.flush();

      // when
      Profile profile = Profile.create(user);

      // then
      assertThat(profile.getTemperatureSensitivity()).isEqualTo(3);
      assertThat(profile.getGender()).isNull();
      assertThat(profile.getLocation()).isNull();
    }
  }

  @Nested
  @DisplayName("User와 함께 조회 (findByIdWithUser)")
  class FindByIdWithUser {

    @Test
    @DisplayName("존재하는 userId로 조회하면 User가 함께 로딩된 Profile을 반환한다")
    void 존재하는_userId로_조회하면_User가_함께_로딩된_Profile을_반환한다() {
      // given
      User user = userRepository.save(User.create("홍길동", "hong3@test.com", "encoded-password"));
      profileRepository.save(Profile.create(user));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      Optional<Profile> found = profileRepository.findByIdWithUser(user.getId());

      // then
      assertThat(found).isPresent();
      assertThat(found.get().getUser().getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 빈 Optional을 반환한다")
    void 존재하지_않는_userId로_조회하면_빈_Optional을_반환한다() {
      // when
      Optional<Profile> found = profileRepository.findByIdWithUser(UUID.randomUUID());

      // then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("격자 좌표로 조회 (findByLocation)")
  class FindByLocation {

    @Test
    @DisplayName("location 좌표가 일치하는 프로필만 반환하고 위치 미등록 프로필은 제외한다")
    void location_좌표가_일치하는_프로필만_반환하고_위치_미등록_프로필은_제외한다() {
      // given
      User matchingUser = userRepository.save(
          User.create("홍길동", "hong4@test.com", "encoded-password"));
      Profile matchingProfile = Profile.create(matchingUser);
      matchingProfile.changeProfile(null, null,
          Location.create(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구")), 3);
      profileRepository.save(matchingProfile);

      User otherGridUser = userRepository.save(
          User.create("김철수", "kim@test.com", "encoded-password"));
      Profile otherGridProfile = Profile.create(otherGridUser);
      otherGridProfile.changeProfile(null, null,
          Location.create(35.1, 129.0, 98, 76, List.of("부산광역시")), 3);
      profileRepository.save(otherGridProfile);

      User noLocationUser = userRepository.save(
          User.create("박영희", "park@test.com", "encoded-password"));
      profileRepository.save(Profile.create(noLocationUser));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Profile> found = profileRepository.findByLocation(60, 127);

      // then
      assertThat(found).extracting(Profile::getId).containsExactly(matchingProfile.getId());
    }
  }

  @Nested
  @DisplayName("이미지 키 전체 조회 (findAllProfileImageUrls)")
  class FindAllProfileImageUrls {

    @Test
    @DisplayName("이미지가_등록된_프로필의_키만_모아서_반환한다")
    void 이미지가_등록된_프로필의_키만_모아서_반환한다() {
      // given
      User withImageUser = userRepository.save(
          User.create("홍길동", "hong5@test.com", "encoded-password"));
      Profile withImageProfile = Profile.create(withImageUser);
      withImageProfile.changeProfileImageUrl("profile/a.png");
      profileRepository.save(withImageProfile);

      User withoutImageUser = userRepository.save(
          User.create("김철수", "kim2@test.com", "encoded-password"));
      profileRepository.save(Profile.create(withoutImageUser));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      Set<String> imageUrls = profileRepository.findAllProfileImageUrls();

      // then
      assertThat(imageUrls).containsExactly("profile/a.png");
    }
  }
}
