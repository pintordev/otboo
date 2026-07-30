package com.sprint.mission.otboo.domain.authuser.user.mapper;

import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

  private UserMapper userMapper;

  @BeforeEach
  void setUp() {
    userMapper = new UserMapper();
  }

  @Nested
  @DisplayName("userDtoFrom(User)")
  class UserDtoFromUser {

    @Test
    @DisplayName("User 엔티티를 UserDto로 정확히 변환한다")
    void userDtoFrom_user_mapsAllFieldsCorrectly() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");

      // when
      UserDto dto = userMapper.userDtoFrom(user);

      // then
      assertThat(dto.id()).isEqualTo(user.getId());
      assertThat(dto.email()).isEqualTo(user.getEmail());
      assertThat(dto.name()).isEqualTo(user.getName());
      assertThat(dto.role()).isEqualTo(Role.USER);
      assertThat(dto.locked()).isFalse();
      assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("ADMIN 권한 User도 role 필드에 정확히 매핑된다")
    void userDtoFrom_user_mapsAdminRole() {
      // given
      User admin = User.createAdmin("관리자", "admin@test.com", "encoded-password");

      // when
      UserDto dto = userMapper.userDtoFrom(admin);

      // then
      assertThat(dto.role()).isEqualTo(Role.ADMIN);
    }
  }

  @Nested
  @DisplayName("userDtoFrom(CustomUserDetails)")
  class UserDtoFromCustomUserDetails {

    @Test
    @DisplayName("CustomUserDetails를 UserDto로 정확히 변환한다")
    void userDtoFrom_customUserDetails_mapsAllFieldsCorrectly() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      // when
      UserDto dto = userMapper.userDtoFrom(principal);

      // then
      assertThat(dto.id()).isEqualTo(user.getId());
      assertThat(dto.email()).isEqualTo(user.getEmail());
      assertThat(dto.name()).isEqualTo(user.getName());
      assertThat(dto.role()).isEqualTo(Role.USER);
      assertThat(dto.locked()).isFalse();
      assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("잠긴 계정의 CustomUserDetails는 locked=true로 매핑된다")
    void userDtoFrom_customUserDetails_mapsLockedState() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      user.lock(LockReason.ADMIN_ACTION);
      CustomUserDetails principal = new CustomUserDetails(user);

      // when
      UserDto dto = userMapper.userDtoFrom(principal);

      // then
      assertThat(dto.locked()).isTrue();
    }
  }
}
