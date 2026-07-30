package com.sprint.mission.otboo.domain.authuser.user.controller;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(UserController.class)
class UserControllerTest {

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  @Nested
  @DisplayName("회원가입 성공")
  class SignUpSuccess {

    @Test
    @DisplayName("유효한 요청으로 회원가입 시 201과 UserDto를 반환한다")
    void signUp_validRequest_returns201() throws Exception {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();
      UserDto responseDto = new UserDto(
          UUID.randomUUID(), Instant.now(), request.email(), request.name(), Role.USER, false
      );
      given(userService.signUp(any(UserCreateRequest.class))).willReturn(responseDto);

      // when & then
      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value(request.email()))
          .andExpect(jsonPath("$.name").value(request.name()))
          .andExpect(jsonPath("$.role").value("USER"))
          .andExpect(jsonPath("$.locked").value(false));
    }
  }

  @Nested
  @DisplayName("회원가입 유효성 검증")
  class SignUpValidation {

    @Test
    @DisplayName("이름이 비어있으면 400을 반환한다")
    void signUp_blankName_returns400() throws Exception {
      UserCreateRequest request = new UserCreateRequest("", "hong@test.com", "password123");

      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
    void signUp_invalidEmailFormat_returns400() throws Exception {
      UserCreateRequest request = new UserCreateRequest("홍길동", "invalid-email", "password123");

      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일이 비어있으면 400을 반환한다")
    void signUp_blankEmail_returns400() throws Exception {
      UserCreateRequest request = new UserCreateRequest("홍길동", "", "password123");

      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 6자 미만이면 400을 반환한다")
    void signUp_passwordTooShort_returns400() throws Exception {
      UserCreateRequest request = new UserCreateRequest("홍길동", "hong@test.com", "1234");

      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 비어있으면 400을 반환한다")
    void signUp_blankPassword_returns400() throws Exception {
      UserCreateRequest request = new UserCreateRequest("홍길동", "hong@test.com", "");

      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("이메일 중복")
  class SignUpDuplicateEmail {

    @Test
    @DisplayName("중복된 이메일로 회원가입 시 409를 반환한다")
    void signUp_duplicateEmail_returns409() throws Exception {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();
      given(userService.signUp(any(UserCreateRequest.class)))
          .willThrow(DuplicateEmailException.withEmail(request.email()));

      // when & then
      mockMvc.perform(post("/api/users")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict());
    }
  }
}
