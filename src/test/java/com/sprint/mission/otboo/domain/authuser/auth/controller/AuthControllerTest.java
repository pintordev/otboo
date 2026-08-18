package com.sprint.mission.otboo.domain.authuser.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.RefreshDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.InvalidCredentialsException;
import com.sprint.mission.otboo.domain.authuser.auth.service.AuthService;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.security.cookie.provider.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController")
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private RefreshTokenCookieProvider refreshTokenCookieProvider;

  private UserDto userDto() {
    return new UserDto(UUID.randomUUID(), Instant.now(), "hong@test.com", "홍길동", Role.USER, false);
  }

  @Nested
  @DisplayName("로그아웃 - POST /api/auth/sign-out")
  class SignOut {

    @Test
    @DisplayName("정상 요청이면 204를 반환하고 refresh 쿠키를 삭제한다")
    void 정상_요청이면_204를_반환하고_refresh_쿠키를_삭제한다() throws Exception {
      // given & when & then
      mockMvc.perform(post("/api/auth/sign-out")
              .cookie(new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "refresh-token")))
          .andExpect(status().isNoContent());

      verify(authService).signOut("refresh-token");
      verify(refreshTokenCookieProvider).clear(any());
    }
  }

  @Nested
  @DisplayName("로그인 - POST /api/auth/sign-in")
  class SignIn {

    @Test
    @DisplayName("정상 요청이면 200과 JwtDto를 반환하고 refresh 쿠키를 발급한다")
    void 정상_요청이면_200과_JwtDto를_반환하고_refresh_쿠키를_발급한다() throws Exception {
      // given
      UserDto userDto = userDto();
      JwtDto jwtDto = new JwtDto(userDto, "access-token");
      SignInDto signInDto = new SignInDto(jwtDto, "refresh-token");
      given(authService.signIn(any())).willReturn(signInDto);

      // when & then
      mockMvc.perform(post("/api/auth/sign-in")
              .param("username", "hong@test.com")
              .param("password", "password1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("access-token"))
          .andExpect(jsonPath("$.userDto.email").value("hong@test.com"));

      verify(refreshTokenCookieProvider).attach(any(), eq("refresh-token"));
    }

    @Test
    @DisplayName("아이디가 비어있으면 400을 반환한다")
    void 아이디가_비어있으면_400을_반환한다() throws Exception {
      // when & then
      mockMvc.perform(post("/api/auth/sign-in")
              .param("username", "")
              .param("password", "password1"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 틀리면 401을 반환한다")
    void 아이디_또는_비밀번호가_틀리면_401을_반환한다() throws Exception {
      // given
      willThrow(InvalidCredentialsException.withNone()).given(authService).signIn(any());

      // when & then
      mockMvc.perform(post("/api/auth/sign-in")
              .param("username", "hong@test.com")
              .param("password", "wrongpass1"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("계정이 잠겨있으면 403을 반환한다")
    void 계정이_잠겨있으면_403을_반환한다() throws Exception {
      // given
      willThrow(AccountLockedException.withNone()).given(authService).signIn(any());

      // when & then
      mockMvc.perform(post("/api/auth/sign-in")
              .param("username", "hong@test.com")
              .param("password", "password1"))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("임시 비밀번호 발급 - POST /api/auth/reset-password")
  class ResetPassword {

    @Test
    @DisplayName("정상 요청이면 204를 반환한다")
    void 정상_요청이면_204를_반환한다() throws Exception {
      // when & then
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"hong@test.com\"}"))
          .andExpect(status().isNoContent());

      verify(authService).resetPassword(new ResetPasswordRequest("hong@test.com"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
    void 이메일_형식이_올바르지_않으면_400을_반환한다() throws Exception {
      // when & then
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"not-an-email\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 404를 반환한다")
    void 가입되지_않은_이메일이면_404를_반환한다() throws Exception {
      // given
      willThrow(UserNotFoundException.withNone()).given(authService).resetPassword(any());

      // when & then
      mockMvc.perform(post("/api/auth/reset-password")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"unknown@test.com\"}"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("액세스 토큰 재발급 - POST /api/auth/refresh")
  class Refresh {

    @Test
    @DisplayName("정상 요청이면 200과 JwtDto를 반환하고 새 refresh 쿠키를 발급한다")
    void 정상_요청이면_200과_JwtDto를_반환하고_새_refresh_쿠키를_발급한다() throws Exception {
      // given
      UserDto userDto = userDto();
      JwtDto jwtDto = new JwtDto(userDto, "new-access-token");
      RefreshDto refreshDto = new RefreshDto(jwtDto, "new-refresh-token");
      given(authService.refresh("refresh-token")).willReturn(refreshDto);

      // when & then
      mockMvc.perform(post("/api/auth/refresh")
              .cookie(new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "refresh-token")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("new-access-token"));

      verify(refreshTokenCookieProvider).attach(any(), eq("new-refresh-token"));
    }

    @Test
    @DisplayName("리프레시 토큰이 유효하지 않으면 401을 반환한다")
    void 리프레시_토큰이_유효하지_않으면_401을_반환한다() throws Exception {
      // given
      willThrow(InvalidRefreshTokenException.withNone())
          .given(authService).refresh("invalid-token");

      // when & then
      mockMvc.perform(post("/api/auth/refresh")
              .cookie(new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "invalid-token")))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰의 사용자를 찾을 수 없으면 404를 반환한다")
    void 토큰의_사용자를_찾을_수_없으면_404를_반환한다() throws Exception {
      // given
      willThrow(UserNotFoundException.withNone()).given(authService).refresh("refresh-token");

      // when & then
      mockMvc.perform(post("/api/auth/refresh")
              .cookie(new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "refresh-token")))
          .andExpect(status().isNotFound());
    }
  }
}
