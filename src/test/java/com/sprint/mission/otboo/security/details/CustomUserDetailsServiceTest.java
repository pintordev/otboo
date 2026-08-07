package com.sprint.mission.otboo.security.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService")
class CustomUserDetailsServiceTest {

  @InjectMocks
  private CustomUserDetailsService customUserDetailsService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserMapper userMapper;

  @Nested
  @DisplayName("사용자 조회 (loadUserByUsername)")
  class LoadUserByUsername {

    @Test
    @DisplayName("이메일로 사용자를 찾으면 CustomUserDetails를 반환한다")
    void 이메일로_사용자를_찾으면_CustomUserDetails를_반환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      UserDto userDto = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), user.getRole(), user.isLocked());
      given(userRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(userMapper.userDtoFrom(user)).willReturn(userDto);

      // when
      UserDetails result = customUserDetailsService.loadUserByUsername("hong@test.com");

      // then
      assertThat(result).isInstanceOf(CustomUserDetails.class);
      CustomUserDetails customUserDetails = (CustomUserDetails) result;
      assertThat(customUserDetails.getUserDto()).isEqualTo(userDto);
      assertThat(customUserDetails.getPassword()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("이메일로 사용자를 찾지 못하면 UsernameNotFoundException을 던진다")
    void 이메일로_사용자를_찾지_못하면_UsernameNotFoundException을_던진다() {
      // given
      given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown@test.com"))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }
}
