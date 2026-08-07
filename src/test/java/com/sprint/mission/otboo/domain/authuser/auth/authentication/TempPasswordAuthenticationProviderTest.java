package com.sprint.mission.otboo.domain.authuser.auth.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("TempPasswordAuthenticationProvider")
class TempPasswordAuthenticationProviderTest {

  @InjectMocks
  private TempPasswordAuthenticationProvider tempPasswordAuthenticationProvider;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserMapper userMapper;

  @Mock
  private TempPasswordRegistry tempPasswordRegistry;

  @Nested
  @DisplayName("임시 비밀번호 인증 (authenticate)")
  class AuthenticateMethod {

    @Test
    @DisplayName("이메일과 임시 비밀번호가 일치하면 인증된 Authentication을 반환한다")
    void 이메일과_임시_비밀번호가_일치하면_인증된_Authentication을_반환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      UserDto userDto = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), user.getRole(), user.isLocked());
      given(userRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(tempPasswordRegistry.matches(user.getId(), "temp-password!")).willReturn(true);
      given(userMapper.userDtoFrom(user)).willReturn(userDto);

      Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
          "hong@test.com", "temp-password!");

      // when
      Authentication result = tempPasswordAuthenticationProvider.authenticate(request);

      // then
      assertThat(result.isAuthenticated()).isTrue();
      assertThat(result.getPrincipal()).isInstanceOf(CustomUserDetails.class);
      CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
      assertThat(principal.getUserDto()).isEqualTo(userDto);
      assertThat(result.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("USER");
    }

    @Test
    @DisplayName("이메일로 사용자를 찾을 수 없으면 BadCredentialsException을 던진다")
    void 이메일로_사용자를_찾을_수_없으면_BadCredentialsException을_던진다() {
      // given
      given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());
      Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
          "unknown@test.com", "temp-password!");

      // when & then
      assertThatThrownBy(() -> tempPasswordAuthenticationProvider.authenticate(request))
          .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("임시 비밀번호가 일치하지 않으면 BadCredentialsException을 던진다")
    void 임시_비밀번호가_일치하지_않으면_BadCredentialsException을_던진다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      given(userRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(tempPasswordRegistry.matches(user.getId(), "wrong-password")).willReturn(false);

      Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
          "hong@test.com", "wrong-password");

      // when & then
      assertThatThrownBy(() -> tempPasswordAuthenticationProvider.authenticate(request))
          .isInstanceOf(BadCredentialsException.class);
    }
  }

  @Nested
  @DisplayName("지원 여부 확인 (supports)")
  class Supports {

    @Test
    @DisplayName("UsernamePasswordAuthenticationToken 타입이면 true를 반환한다")
    void UsernamePasswordAuthenticationToken_타입이면_true를_반환한다() {
      // when & then
      assertThat(tempPasswordAuthenticationProvider.supports(UsernamePasswordAuthenticationToken.class))
          .isTrue();
    }

    @Test
    @DisplayName("그 외 타입이면 false를 반환한다")
    void 그_외_타입이면_false를_반환한다() {
      // when & then
      assertThat(tempPasswordAuthenticationProvider.supports(Authentication.class)).isFalse();
    }
  }
}
