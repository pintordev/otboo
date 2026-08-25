package com.sprint.mission.otboo.domain.authuser.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.authuser.auth.service.AuthService;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.config.SecurityConfig;
import com.sprint.mission.otboo.security.cookie.provider.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.security.details.CustomUserDetailsService;
import com.sprint.mission.otboo.security.oauth2.handler.OAuth2LoginFailureHandler;
import com.sprint.mission.otboo.security.oauth2.handler.OAuth2LoginSuccessHandler;
import com.sprint.mission.otboo.security.oauth2.repository.HttpCookieOAuth2AuthorizationRequestRepository;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// CSRF 토큰 발급은 SecurityConfig의 실제 필터 체인(CookieCsrfTokenRepository)이 있어야 검증 의미가 있어서
// 다른 AuthController 테스트와 달리 SecurityConfig를 그대로 Import한다.
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController - CSRF 토큰 발급")
class AuthControllerCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private RefreshTokenCookieProvider refreshTokenCookieProvider;

  @MockitoBean
  private CustomUserDetailsService customUserDetailsService;

  @MockitoBean
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private UserMapper userMapper;

  @MockitoBean
  private TempPasswordRegistry tempPasswordRegistry;

  @MockitoBean
  private TokenProvider tokenProvider;

  @MockitoBean
  private UserSessionRegistry userSessionRegistry;

  @MockitoBean
  private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  @MockitoBean
  private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

  @MockitoBean
  private HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

  @Nested
  @DisplayName("CSRF 토큰 발급 - GET /api/auth/csrf-token")
  class CsrfToken {

    @Test
    @DisplayName("익명 사용자도 204와 함께 XSRF-TOKEN 쿠키를 발급받는다")
    void 익명_사용자도_204와_함께_XSRFTOKEN_쿠키를_발급받는다() throws Exception {
      // given & when & then
      mockMvc.perform(get("/api/auth/csrf-token"))
          .andExpect(status().isNoContent())
          .andExpect(cookie().exists("XSRF-TOKEN"));
    }
  }
}
