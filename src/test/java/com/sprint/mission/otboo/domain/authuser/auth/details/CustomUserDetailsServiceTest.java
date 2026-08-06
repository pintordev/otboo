package com.sprint.mission.otboo.domain.authuser.auth.details;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
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

  @Test
  @DisplayName("존재하는 이메일이면 CustomUserDetails를 반환한다")
  void loadUserByUsername_existingEmail_returnsCustomUserDetails() {
    User user = User.create("홍길동", "hong@test.com", "encoded-password");
    given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));

    UserDetails result = customUserDetailsService.loadUserByUsername("hong@test.com");

    assertThat(result).isInstanceOf(CustomUserDetails.class);
    assertThat(result.getUsername()).isEqualTo("hong@test.com");
  }

  @Test
  @DisplayName("존재하지 않는 이메일이면 UsernameNotFoundException을 던진다")
  void loadUserByUsername_unknownEmail_throwsUsernameNotFoundException() {
    given(mockUserRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown@test.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
