package com.sprint.mission.otboo.domain.weathernotification.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(NotificationController.class)
@Import(NotificationControllerTest.SecurityArgumentResolverConfig.class)
@DisplayName("NotificationController")
class NotificationControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  NotificationService notificationService;

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
  @DisplayName("알림 목록 조회 - GET /api/notifications")
  class GetNotifications {

    @Test
    @DisplayName("정상 요청이면 200과 현재 로그인 사용자 기준 CursorPageResponse를 반환한다")
    void 정상_요청이면_200과_현재_로그인_사용자_기준_CursorPageResponse를_반환한다() throws Exception {
      // given
      UUID currentUserId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(currentUserId));

      NotificationDto dto = new NotificationDto(
          UUID.randomUUID(), Instant.now(), currentUserId, "제목", "내용", NotificationLevel.INFO);
      CursorPageResponse<NotificationDto> response = new CursorPageResponse<>(
          List.of(dto), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      when(notificationService.getNotifications(eq(currentUserId), any(NotificationListParams.class)))
          .thenReturn(response);

      // when & then
      mockMvc.perform(get("/api/notifications").param("limit", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.sortBy").value("createdAt"))
          .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

      ArgumentCaptor<UUID> receiverIdCaptor = ArgumentCaptor.forClass(UUID.class);
      verify(notificationService).getNotifications(
          receiverIdCaptor.capture(), any(NotificationListParams.class));
      assertThat(receiverIdCaptor.getValue()).isEqualTo(currentUserId);
    }

    @Test
    @DisplayName("limit이 없으면 400을 반환한다")
    void limit이_없으면_400을_반환한다() throws Exception {
      // given
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(UUID.randomUUID()));

      // when & then
      mockMvc.perform(get("/api/notifications"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("limit이 100을 초과하면 400을 반환한다")
    void limit이_100을_초과하면_400을_반환한다() throws Exception {
      // given
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(UUID.randomUUID()));

      // when & then
      mockMvc.perform(get("/api/notifications").param("limit", "101"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cursor만 있고 idAfter가 없으면 400을 반환한다")
    void cursor만_있고_idAfter가_없으면_400을_반환한다() throws Exception {
      // given
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(UUID.randomUUID()));

      // when & then
      mockMvc.perform(get("/api/notifications")
              .param("limit", "10")
              .param("cursor", Instant.now().toString()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cursor 형식이 ISO-8601이 아니면 400을 반환한다")
    void cursor_형식이_ISO_8601이_아니면_400을_반환한다() throws Exception {
      // given
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(UUID.randomUUID()));

      // when & then
      mockMvc.perform(get("/api/notifications")
              .param("limit", "10")
              .param("cursor", "invalid-instant")
              .param("idAfter", UUID.randomUUID().toString()))
          .andExpect(status().isBadRequest());
    }
  }
}