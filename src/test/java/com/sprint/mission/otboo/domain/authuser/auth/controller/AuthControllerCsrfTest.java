package com.sprint.mission.otboo.domain.authuser.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.authuser.auth.service.AuthService;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.config.SecurityConfig;
import com.sprint.mission.otboo.security.cookie.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.domain.authuser.auth.details.CustomUserDetailsService;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
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
  private TempPasswordRegistry tempPasswordRegistry;
  @MockitoBean
  private TokenProvider tokenProvider;
  @MockitoBean
  private UserSessionRegistry userSessionRegistry;

  @Test
  @DisplayName("익명 사용자가 GET /api/auth/csrf-token을 호출하면 204와 함께 XSRF-TOKEN 쿠키를 발급받는다")
  void csrfToken_anonymous_returns204AndSetsXsrfTokenCookie() throws Exception {
    mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
  }
}
