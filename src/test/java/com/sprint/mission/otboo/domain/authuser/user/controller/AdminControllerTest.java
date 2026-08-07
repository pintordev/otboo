package com.sprint.mission.otboo.domain.authuser.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.service.AdminService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// SecurityConfig가 @EnableMethodSecurity(proxyTargetClass = true)로 CGLIB 프록시를 강제하도록
// 바뀌었다. AdminController가 AdminApi 인터페이스를 구현하는데, @GetMapping/@PatchMapping은
// 구현 클래스 쪽에만 붙어있어서 기본 JDK 프록시(인터페이스만 노출)로는 핸들러 매핑이
// 이 애노테이션을 못 찾았던 것으로 보인다. CGLIB(클래스 상속)로 강제하면 애노테이션이
// 그대로 보여서 @WebMvcTest 슬라이스에서도 정상 라우팅됨을 실제로 확인했다.
// 다만 @WebMvcTest는 SecurityConfig를 자동으로 가져오지 않으므로, @PreAuthorize 자체를
// 검증하려면 이 슬라이스에도 동일하게 proxyTargetClass=true로 메서드 시큐리티를 켜줘야 한다.
// SecurityMockMvcRequestPostProcessors.authentication(...)은 이 슬라이스(실제 SecurityFilterChain이
// 없는 상태)에서는 SecurityContext가 메서드 시큐리티 인터셉터까지 안 전달돼서 500이 났다
// (AuthenticationCredentialsNotFoundException, 실제로 돌려서 확인함). 그래서 AuthControllerTest의
// SignOut 테스트와 동일하게 SecurityContextHolder를 직접 설정하는 방식을 쓴다.
@WebMvcTest(AdminController.class)
@Import(AdminControllerTest.MethodSecurityConfig.class)
@DisplayName("AdminController")
class AdminControllerTest {

  @TestConfiguration
  @EnableMethodSecurity(proxyTargetClass = true)
  static class MethodSecurityConfig {

  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AdminService adminService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAsAdmin() {
    Authentication authentication = new UsernamePasswordAuthenticationToken(
        "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private void authenticateAsUser() {
    Authentication authentication = new UsernamePasswordAuthenticationToken(
        "user", null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private UserDto userDto(UUID id, Role role, boolean locked) {
    return new UserDto(id, Instant.now(), "hong@test.com", "홍길동", role, locked);
  }

  @Nested
  @DisplayName("사용자 목록 조회 - GET /api/users")
  class SearchUserList {

    @Test
    @DisplayName("관리자면 200과 목록을 반환한다")
    void 관리자면_200과_목록을_반환한다() throws Exception {
      // given
      authenticateAsAdmin();
      CursorPageResponse<UserDto> response = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "email", SortDirection.ASCENDING);
      given(adminService.searchUserList(any())).willReturn(response);

      // when & then
      mockMvc.perform(get("/api/users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sortBy").value("email"));
    }

    @Test
    @DisplayName("관리자가 아니면 403을 반환한다")
    void 관리자가_아니면_403을_반환한다() throws Exception {
      // given
      authenticateAsUser();

      // when & then
      mockMvc.perform(get("/api/users"))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("limit이 범위를 벗어나면 400을 반환한다")
    void limit이_범위를_벗어나면_400을_반환한다() throws Exception {
      // given
      authenticateAsAdmin();

      // when & then
      mockMvc.perform(get("/api/users").param("limit", "0"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("역할 변경 - PATCH /api/users/{userId}/role")
  class ChangeRole {

    @Test
    @DisplayName("관리자면 200과 변경된 UserDto를 반환한다")
    void 관리자면_200과_변경된_UserDto를_반환한다() throws Exception {
      // given
      authenticateAsAdmin();
      UUID userId = UUID.randomUUID();
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);
      given(adminService.changeRole(eq(userId), any()))
          .willReturn(userDto(userId, Role.ADMIN, false));

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("관리자가 아니면 403을 반환한다")
    void 관리자가_아니면_403을_반환한다() throws Exception {
      // given
      authenticateAsUser();
      UUID userId = UUID.randomUUID();
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("role이 없으면 400을 반환한다")
    void changeRole_missingRole_returns400() throws Exception {
      // given
      authenticateAsAdmin();

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", UUID.randomUUID())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void 존재하지_않는_사용자면_404를_반환한다() throws Exception {
      // given
      authenticateAsAdmin();
      UUID userId = UUID.randomUUID();
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);
      willThrow(UserNotFoundException.withNone()).given(adminService).changeRole(any(), any());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("잠금 상태 변경 - PATCH /api/users/{userId}/lock")
  class ChangeLock {

    @Test
    @DisplayName("관리자면 200과 변경된 UserDto를 반환한다")
    void 관리자면_200과_변경된_UserDto를_반환한다() throws Exception {
      // given
      authenticateAsAdmin();
      UUID userId = UUID.randomUUID();
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);
      given(adminService.changeLock(eq(userId), any()))
          .willReturn(userDto(userId, Role.USER, true));

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/lock", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    @DisplayName("관리자가 아니면 403을 반환한다")
    void 관리자가_아니면_403을_반환한다() throws Exception {
      // given
      authenticateAsUser();
      UUID userId = UUID.randomUUID();
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/lock", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("locked 값이 없으면 400을 반환한다")
    void locked_값이_없으면_400을_반환한다() throws Exception {
      // given
      authenticateAsAdmin();
      UUID userId = UUID.randomUUID();

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/lock", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void changeLock_userNotFound_returns404() throws Exception {
      // given
      authenticateAsAdmin();
      UUID userId = UUID.randomUUID();
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);
      willThrow(UserNotFoundException.withNone()).given(adminService).changeLock(any(), any());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/lock", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound());
    }
  }
}
