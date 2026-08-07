package com.sprint.mission.otboo.domain.authuser.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.ChangePasswordRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ProfileUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.AccessDeniedException;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.service.UserService;
import com.sprint.mission.otboo.security.details.UserPrincipal;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(UserController.class)
@Import(UserControllerTest.SecurityArgumentResolverConfig.class)
@DisplayName("UserController")
class UserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  private Authentication authenticationOf(UUID userId) {
    UserPrincipal principal = new UserPrincipal(userId, "USER");
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority("USER")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @TestConfiguration
  static class SecurityArgumentResolverConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
      resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
  }

  @Nested
  @DisplayName("회원가입 - POST /api/users")
  class SignUp {

    @Test
    @DisplayName("정상 요청이면 201과 UserDto를 반환한다")
    void 정상_요청이면_201과_UserDto를_반환한다() throws Exception {
      // given
      UserCreateRequest request = new UserCreateRequest("hong@test.com", "password1", "홍길동");
      UserDto response =
          new UserDto(UUID.randomUUID(), Instant.now(), "hong@test.com", "홍길동", Role.USER, false);
      given(userService.signUp(any())).willReturn(response);

      // when & then
      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value("hong@test.com"));
    }

    @Test
    @DisplayName("이메일이 비어있으면 400을 반환한다")
    void 이메일이_비어있으면_400을_반환한다() throws Exception {
      // given
      UserCreateRequest request = new UserCreateRequest("", "password1", "홍길동");

      // when & then
      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409를 반환한다")
    void 이미_가입된_이메일이면_409를_반환한다() throws Exception {
      // given
      UserCreateRequest request = new UserCreateRequest("hong@test.com", "password1", "홍길동");
      willThrow(DuplicateEmailException.withEmail("hong@test.com")).given(userService).signUp(any());

      // when & then
      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  @DisplayName("프로필 조회 - GET /api/users/{userId}/profiles")
  class GetProfile {

    @Test
    @DisplayName("본인이면 200과 ProfileDto를 반환한다")
    void 본인이면_200과_ProfileDto를_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      ProfileDto response = new ProfileDto(userId, "홍길동", null, null, null, 3, null);
      given(userService.getProfile(userId, userId)).willReturn(response);

      // when & then
      mockMvc.perform(get("/api/users/{userId}/profiles", userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("홍길동"));
    }

    @Test
    @DisplayName("본인이 아니면 403을 반환한다")
    void 본인이_아니면_403을_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(otherId));
      willThrow(AccessDeniedException.withNone()).given(userService).getProfile(userId, otherId);

      // when & then
      mockMvc.perform(get("/api/users/{userId}/profiles", userId))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("프로필 수정 - PATCH /api/users/{userId}/profiles")
  class ChangeProfile {

    @Test
    @DisplayName("본인이면 200과 수정된 ProfileDto를 반환한다")
    void 본인이면_200과_수정된_ProfileDto를_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      ProfileUpdateRequest request = new ProfileUpdateRequest("김철수", null, null, null, 3);
      MockMultipartFile requestPart = new MockMultipartFile(
          "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

      ProfileDto response = new ProfileDto(userId, "김철수", null, null, null, 3, null);
      given(userService.changeProfile(eq(userId), any(), any(), eq(userId))).willReturn(response);

      // when & then
      mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
              .file(requestPart)
              .with(req -> {
                req.setMethod("PATCH");
                return req;
              }))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("김철수"));
    }

    @Test
    @DisplayName("이름이 비어있으면 400을 반환한다")
    void 이름이_비어있으면_400을_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      ProfileUpdateRequest request = new ProfileUpdateRequest("", null, null, null, 3);
      MockMultipartFile requestPart = new MockMultipartFile(
          "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

      // when & then
      mockMvc.perform(multipart("/api/users/{userId}/profiles", userId)
              .file(requestPart)
              .with(req -> {
                req.setMethod("PATCH");
                return req;
              }))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("비밀번호 변경 - PATCH /api/users/{userId}/password")
  class ChangePassword {

    @Test
    @DisplayName("본인이면 200을 반환한다")
    void 본인이면_200을_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      ChangePasswordRequest request = new ChangePasswordRequest("new-password1");

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/password", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 6자 미만이면 400을 반환한다")
    void 비밀번호가_6자_미만이면_400을_반환한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      ChangePasswordRequest request = new ChangePasswordRequest("1234");

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/password", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }
  }
}
