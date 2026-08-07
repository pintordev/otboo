package com.sprint.mission.otboo.domain.authuser.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.RefreshDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthMapper")
class AuthMapperTest {

  private static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  private AuthMapper authMapper;

  @Mock
  private UserMapper userMapper;

  @Nested
  @DisplayName("로그인 응답 변환 (signInDtoFrom)")
  class SignInDtoFrom {

    @Test
    @DisplayName("UserDto와 토큰들을 JwtDto로 감싸 SignInDto로 변환한다")
    void UserDto와_토큰들을_JwtDto로_감싸_SignInDto로_변환한다() {
      // given
      UserDto userDto = fm.giveMeBuilder(UserDto.class).sample();

      // when
      SignInDto result = authMapper.signInDtoFrom(userDto, "access-token", "refresh-token");

      // then
      assertThat(result.jwtDto().userDto()).isEqualTo(userDto);
      assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
      assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }
  }

  @Nested
  @DisplayName("재발급 응답 변환 (refreshDtoFrom)")
  class RefreshDtoFrom {

    @Test
    @DisplayName("User를 UserDto로 매핑하고 토큰들과 함께 JwtDto로 감싸 RefreshDto로 변환한다")
    void User를_UserDto로_매핑하고_토큰들과_함께_JwtDto로_감싸_RefreshDto로_변환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      UserDto userDto = fm.giveMeBuilder(UserDto.class).sample();
      given(userMapper.userDtoFrom(user)).willReturn(userDto);

      // when
      RefreshDto result = authMapper.refreshDtoFrom(user, "new-access-token", "new-refresh-token");

      // then
      assertThat(result.jwtDto().userDto()).isEqualTo(userDto);
      assertThat(result.jwtDto().accessToken()).isEqualTo("new-access-token");
      assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }
  }
}
