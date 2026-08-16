package com.sprint.mission.otboo.domain.authuser.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.LocationRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.LocationDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Gender;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.util.FileUrlResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserMapper")
class UserMapperTest {

  private final FileProperties fileProperties = new FileProperties(
      FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);

  private final UserMapper userMapper = new UserMapper(new FileUrlResolver(fileProperties));

  @Nested
  @DisplayName("User -> UserDto 변환 (userDtoFrom)")
  class UserDtoFrom {

    @Test
    @DisplayName("User 엔티티의 필드를 그대로 UserDto로 변환한다")
    void User_엔티티의_필드를_그대로_UserDto로_변환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");

      // when
      UserDto result = userMapper.userDtoFrom(user);

      // then
      assertThat(result.id()).isEqualTo(user.getId());
      assertThat(result.email()).isEqualTo(user.getEmail());
      assertThat(result.name()).isEqualTo(user.getName());
      assertThat(result.role()).isEqualTo(user.getRole());
      assertThat(result.locked()).isEqualTo(user.isLocked());
      assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
    }
  }

  @Nested
  @DisplayName("Profile -> ProfileDto 변환 (profileDtoFrom)")
  class ProfileDtoFrom {

    @Test
    @DisplayName("연관된 User의 이름을 포함하고 프로필 이미지 키를 URL로 변환해 ProfileDto로 변환한다")
    void 연관된_User의_이름을_포함하고_프로필_이미지_키를_URL로_변환해_ProfileDto로_변환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      Profile profile = Profile.create(user);
      Location location = Location.create(37.5, 127.0, 60, 127, List.of("서울특별시"));
      profile.changeProfile(Gender.MALE, LocalDate.of(1995, 1, 1), location, 4);
      profile.changeProfileImageUrl("profile/uuid.jpg");

      // when
      ProfileDto result = userMapper.profileDtoFrom(profile);

      // then
      assertThat(result.userId()).isEqualTo(profile.getId());
      assertThat(result.name()).isEqualTo("홍길동");
      assertThat(result.gender()).isEqualTo(Gender.MALE);
      assertThat(result.birthDate()).isEqualTo(LocalDate.of(1995, 1, 1));
      assertThat(result.temperatureSensitivity()).isEqualTo(4);
      assertThat(result.profileImageUrl()).isEqualTo("http://localhost:8080/uploads/profile/uuid.jpg");
      assertThat(result.location().latitude()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("프로필 이미지 키가 없으면 profileImageUrl은 null이다")
    void 프로필_이미지_키가_없으면_profileImageUrl은_null이다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      Profile profile = Profile.create(user);

      // when
      ProfileDto result = userMapper.profileDtoFrom(profile);

      // then
      assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("location이 없으면 ProfileDto의 location은 null이다")
    void location이_없으면_ProfileDto의_location은_null이다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      Profile profile = Profile.create(user);

      // when
      ProfileDto result = userMapper.profileDtoFrom(profile);

      // then
      assertThat(result.location()).isNull();
    }
  }

  @Nested
  @DisplayName("Location -> LocationDto 변환 (locationDtoFrom)")
  class LocationDtoFrom {

    @Test
    @DisplayName("Location 필드를 그대로 LocationDto로 변환한다")
    void Location_필드를_그대로_LocationDto로_변환한다() {
      // given
      Location location = Location.create(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구"));

      // when
      LocationDto result = userMapper.locationDtoFrom(location);

      // then
      assertThat(result.latitude()).isEqualTo(37.5);
      assertThat(result.longitude()).isEqualTo(127.0);
      assertThat(result.x()).isEqualTo(60);
      assertThat(result.y()).isEqualTo(127);
      assertThat(result.locationNames()).containsExactly("서울특별시", "강남구");
    }

    @Test
    @DisplayName("location이 null이면 null을 반환한다")
    void location이_null이면_null을_반환한다() {
      // when & then
      assertThat(userMapper.locationDtoFrom(null)).isNull();
    }
  }

  @Nested
  @DisplayName("LocationRequest -> Location 변환 (locationFrom)")
  class LocationFrom {

    @Test
    @DisplayName("LocationRequest 필드를 그대로 Location으로 변환한다")
    void LocationRequest_필드를_그대로_Location으로_변환한다() {
      // given
      LocationRequest request = new LocationRequest(37.5, 127.0, 60, 127, List.of("서울특별시"));

      // when
      Location result = userMapper.locationFrom(request);

      // then
      assertThat(result.getLatitude()).isEqualTo(37.5);
      assertThat(result.getLongitude()).isEqualTo(127.0);
      assertThat(result.getLocationX()).isEqualTo(60);
      assertThat(result.getLocationY()).isEqualTo(127);
      assertThat(result.getLocationNames()).containsExactly("서울특별시");
    }

    @Test
    @DisplayName("request가 null이면 null을 반환한다")
    void request가_null이면_null을_반환한다() {
      // when & then
      assertThat(userMapper.locationFrom(null)).isNull();
    }
  }
}
