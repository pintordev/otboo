package com.sprint.mission.otboo.domain.social.feed.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.service.FeedService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
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
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(FeedController.class)
@Import(FeedControllerTest.SecurityArgumentResolverConfig.class)
@DisplayName("FeedController")
class FeedControllerTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  FeedService feedService;

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
  @DisplayName("피드 등록 - POST /api/feeds")
  class CreateFeed {

    @Test
    @DisplayName("정상 요청이면 201과 FeedDto를 반환한다")
    void 정상_요청이면_201과_FeedDto를_반환한다() throws Exception {
      // given
      UUID currentUserId = UUID.randomUUID();
      SecurityContextHolder.getContext().setAuthentication(authenticationOf(currentUserId));

      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", UUID.randomUUID()) // 인증 사용자와 다른 값
          .sample();

      FeedDto response = new FeedDto(
          UUID.randomUUID(), Instant.now(), Instant.now(), "오늘의 착장", 0L, 0, false);
      when(feedService.create(any(FeedCreateRequest.class), eq(currentUserId)))
          .thenReturn(response);

      // when & then
      mockMvc.perform(post("/api/feeds")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.content").value("오늘의 착장"))
          .andExpect(jsonPath("$.likeCount").value(0))
          .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("content가 비어 있으면 400을 반환한다")
    void content가_비어있으면_400을_반환한다() throws Exception {
      // given — content 공백
      FeedCreateRequest request = new FeedCreateRequest(
          UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), "");

      // when & then
      mockMvc.perform(post("/api/feeds")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("피드 목록 조회 - GET /api/feeds")
  class GetFeedList {

    @Test
    @DisplayName("정상 요청이면 200과 CursorPageResponse를 반환한다")
    void 정상_요청이면_200과_CursorPageResponse를_반환한다() throws Exception {
      // given
      CursorPageResponse<FeedDto> response = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      when(feedService.getFeeds(any(FeedListParams.class))).thenReturn(response);

      // when & then
      mockMvc.perform(get("/api/feeds")
              .param("limit", "10")
              .param("sortBy", "createdAt")
              .param("sortDirection", "ASCENDING"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.sortBy").value("createdAt"));

      ArgumentCaptor<FeedListParams> captor = ArgumentCaptor.forClass(FeedListParams.class);
      verify(feedService).getFeeds(captor.capture());
      FeedListParams captured = captor.getValue();
      assertThat(captured.limit()).isEqualTo(10);
      assertThat(captured.sortBy()).isEqualTo(FeedSortBy.CREATED_AT);
      assertThat(captured.sortDirection()).isEqualTo(SortDirection.ASCENDING);
    }

    @Test
    @DisplayName("limit이 1 미만이면 400을 반환한다")
    void limit이_1_미만이면_400을_반환한다() throws Exception {
      // when & then
      mockMvc.perform(get("/api/feeds")
              .param("limit", "0")
              .param("sortBy", "createdAt")
              .param("sortDirection", "ASCENDING"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 정렬 기준이면 기본값(createdAt)으로 처리한다")
    void 잘못된_정렬_기준이면_기본값_createdAt으로_처리한다() throws Exception {
      // given
      CursorPageResponse<FeedDto> response = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      when(feedService.getFeeds(any(FeedListParams.class))).thenReturn(response);

      // when & then
      mockMvc.perform(get("/api/feeds")
              .param("limit", "10")
              .param("sortBy", "unknown")
              .param("sortDirection", "DESCENDING"))
          .andExpect(status().isOk());

      ArgumentCaptor<FeedListParams> captor = ArgumentCaptor.forClass(FeedListParams.class);
      verify(feedService).getFeeds(captor.capture());
      assertThat(captor.getValue().sortBy()).isEqualTo(FeedSortBy.CREATED_AT);
    }

    @Test
    @DisplayName("createdAt 정렬에 잘못된 형식의 커서면 400을 반환한다")
    void createdAt_정렬에_잘못된_형식의_커서면_400을_반환한다() throws Exception {
      // when & then
      mockMvc.perform(get("/api/feeds")
              .param("limit", "10")
              .param("sortBy", "createdAt")
              .param("sortDirection", "DESCENDING")
              .param("cursor", "invalid-instant")
              .param("idAfter", UUID.randomUUID().toString()))
          .andExpect(status().isBadRequest());
    }
  }
}