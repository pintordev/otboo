package com.sprint.mission.otboo.domain.weathernotification.sse.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(SseController.class)
@Import(SseControllerTest.SecurityArgumentResolverConfig.class)
class SseControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private SseService sseService;

  private Authentication authenticationOf(UUID userId) {
    UserPrincipal principal = new UserPrincipal(userId, "USER");
    return new UsernamePasswordAuthenticationToken(principal, null,
        List.of(new SimpleGrantedAuthority("USER")));
  }

  @TestConfiguration
  static class SecurityArgumentResolverConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
      resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
  }

  @Nested
  @DisplayName("구독")
  class Subscribe {

    @AfterEach
    void tearDown() {
      SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된_사용자가_구독하면_200과_text_event_stream으로_응답한다")
    void 인증된_사용자가_구독하면_200과_text_event_stream으로_응답한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      given(sseService.connect(eq(userId), isNull())).willReturn(new SseEmitter());

      // when & then
      mockMvc.perform(get("/api/sse"))
          .andExpect(request().asyncStarted())
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @DisplayName("LastEventId_쿼리_파라미터를_전달하면_connect에_그대로_넘긴다")
    void LastEventId_쿼리_파라미터를_전달하면_connect에_그대로_넘긴다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(userId));
      given(sseService.connect(userId, lastEventId)).willReturn(new SseEmitter());

      // when & then
      mockMvc.perform(get("/api/sse").param("LastEventId", lastEventId.toString()))
          .andExpect(request().asyncStarted());

      verify(sseService).connect(userId, lastEventId);
    }
  }
}