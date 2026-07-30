package com.sprint.mission.otboo.global.security.details;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

  @Nested
  @DisplayName("생성")
  class Construction {

    @Test
    @DisplayName("User의 필드를 그대로 옮겨 담는다")
    void constructor_copiesFieldsFromUser() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");

      // when
      CustomUserDetails principal = new CustomUserDetails(user);

      // then
      assertThat(principal.getUserId()).isEqualTo(user.getId());
      assertThat(principal.getEmail()).isEqualTo(user.getEmail());
      assertThat(principal.getName()).isEqualTo(user.getName());
      assertThat(principal.getRole()).isEqualTo(Role.USER);
      assertThat(principal.isLocked()).isFalse();
      assertThat(principal.getCreatedAt()).isEqualTo(user.getCreatedAt());
      assertThat(principal.getPassword()).isEqualTo(user.getPassword());
    }
  }

  @Nested
  @DisplayName("getUsername")
  class GetUsername {

    @Test
    @DisplayName("이메일을 username으로 사용한다")
    void getUsername_returnsEmail() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      assertThat(principal.getUsername()).isEqualTo("hong@test.com");
    }
  }

  @Nested
  @DisplayName("getAuthorities")
  class GetAuthorities {

    @Test
    @DisplayName("role 이름을 그대로 권한으로 반환한다")
    void getAuthorities_returnsRoleAsAuthority() {
      User admin = User.createAdmin("관리자", "admin@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(admin);

      assertThat(principal.getAuthorities())
          .extracting(Object::toString)
          .containsExactly("ADMIN");
    }
  }

  @Nested
  @DisplayName("isAccountNonLocked")
  class IsAccountNonLocked {

    @Test
    @DisplayName("잠기지 않은 계정은 true를 반환한다")
    void isAccountNonLocked_unlockedUser_returnsTrue() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("잠긴 계정은 false를 반환한다 (DaoAuthenticationProvider가 이 값을 보고 LockedException을 던짐)")
    void isAccountNonLocked_lockedUser_returnsFalse() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      user.lock(LockReason.ADMIN_ACTION);
      CustomUserDetails principal = new CustomUserDetails(user);

      assertThat(principal.isAccountNonLocked()).isFalse();
    }
  }

  @Nested
  @DisplayName("eraseCredentials")
  class EraseCredentials {

    @Test
    @DisplayName("호출하면 비밀번호를 null로 지운다")
    void eraseCredentials_setsPasswordToNull() {
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      principal.eraseCredentials();

      assertThat(principal.getPassword()).isNull();
    }
  }
}
