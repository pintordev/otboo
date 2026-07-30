package com.sprint.mission.otboo.domain.authuser.auth.mapper;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMapperTest {

  private AuthMapper authMapper;

  @BeforeEach
  void setUp() {
    authMapper = new AuthMapper(new UserMapper());
  }

  @Nested
  @DisplayName("signInDtoFrom")
  class SignInDtoFrom {

    @Test
    @DisplayName("CustomUserDetails와 토큰들을 SignInDto로 정확히 변환한다")
    void signInDtoFrom_mapsPrincipalAndTokensCorrectly() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      // when
      SignInDto result = authMapper.signInDtoFrom(principal, "access-token", "refresh-token");

      // then
      assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
      assertThat(result.jwtDto().userDto().email()).isEqualTo(user.getEmail());
      assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }
  }

  @Nested
  @DisplayName("jwtDtoFrom")
  class JwtDtoFrom {

    @Test
    @DisplayName("CustomUserDetails와 accessToken을 JwtDto로 정확히 변환한다")
    void jwtDtoFrom_mapsPrincipalAndAccessTokenCorrectly() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      // when
      JwtDto result = authMapper.jwtDtoFrom(principal, "access-token");

      // then
      assertThat(result.accessToken()).isEqualTo("access-token");
      assertThat(result.userDto().email()).isEqualTo(user.getEmail());
    }
  }
}
