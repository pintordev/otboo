package com.sprint.mission.otboo.domain.social.directmessage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageDto;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.domain.social.directmessage.service.DirectMessageService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(DirectMessageController.class)
@Import(DirectMessageControllerTest.SecurityArgumentResolverConfig.class)
@DisplayName("DirectMessageController")
class DirectMessageControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  DirectMessageService directMessageService;

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
  @DisplayName("DM 목록 조회 - GET /api/direct-messages")
  class GetDms {

    @Test
    @DisplayName("정상 요청이면 200과 CursorPageResponse를 반환한다")
    void 정상_요청이면_200과_CursorPageResponse를_반환한다() throws Exception {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID otherUserId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(currentUserId));

      DirectMessageDto messageDto = new DirectMessageDto(
          UUID.randomUUID(), Instant.now(),
          new UserSummary(otherUserId, "상대", null),
          new UserSummary(currentUserId, "나", null),
          "안녕하세요?");
      CursorPageResponse<DirectMessageDto> response = new CursorPageResponse<>(
          List.of(messageDto), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      when(
          directMessageService.getDirectMessages(eq(currentUserId), any(DirectMessageParams.class)))
          .thenReturn(response);

      // when & then
      mockMvc.perform(get("/api/direct-messages")
              .param("userId", otherUserId.toString())
              .param("limit", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].content").value("안녕하세요?"))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.sortBy").value("createdAt"));

      verify(directMessageService)
          .getDirectMessages(eq(currentUserId), any(DirectMessageParams.class));
    }

    @Test
    @DisplayName("limit이 1 미만이면 400을 반환한다")
    void limit이_1_미만이면_400을_반환한다() throws Exception {
      // given
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(UUID.randomUUID()));

      // when & then
      mockMvc.perform(get("/api/direct-messages")
              .param("userId", UUID.randomUUID().toString())
              .param("limit", "0"))
          .andExpect(status().isBadRequest());
    }
  }
}