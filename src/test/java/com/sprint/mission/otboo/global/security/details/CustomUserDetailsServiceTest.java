package com.sprint.mission.otboo.global.security.details;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @InjectMocks
  CustomUserDetailsService customUserDetailsService;
  @Mock
  UserRepository mockUserRepository;

  @Nested
  @DisplayName("loadUserByUsername")
  class LoadUserByUsername {

    @Test
    @DisplayName("이메일로 사용자를 찾으면 CustomUserDetails로 감싸 반환한다")
    void loadUserByUsername_existingEmail_returnsCustomUserDetails() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));

      // when
      UserDetails result = customUserDetailsService.loadUserByUsername("hong@test.com");

      // then
      assertThat(result).isInstanceOf(CustomUserDetails.class);
      assertThat(((CustomUserDetails) result).getUserId()).isEqualTo(user.getId());
      assertThat(result.getUsername()).isEqualTo("hong@test.com");
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 UsernameNotFoundException을 던진다")
    void loadUserByUsername_nonExistingEmail_throwsUsernameNotFoundException() {
      given(mockUserRepository.findByEmail("notfound@test.com")).willReturn(Optional.empty());

      assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("notfound@test.com"))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }
}
