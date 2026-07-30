package com.sprint.mission.otboo.global.security.jwt.filter;

import com.sprint.mission.otboo.global.security.jwt.JwtProperties;
import com.sprint.mission.otboo.global.security.jwt.JwtProvider;
import com.sprint.mission.otboo.global.usersession.UserSession;
import com.sprint.mission.otboo.global.usersession.UserSessionRegistry;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionExpiredException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  private static final String ACCESS_SECRET =
      "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS1mb3Itand0LXByb3ZpZGVyLXVuaXQtdGVzdC1wbGVhc2U=";
  private static final String REFRESH_SECRET =
      "dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktZm9yLWp3dC1wcm92aWRlci11bml0LXRlc3QtcGxlYXNl";

  @Mock
  UserSessionRegistry mockUserSessionRegistry;
  @Mock
  FilterChain mockFilterChain;

  private JwtProvider jwtProvider;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, 15L, 14L));
    filter = new JwtAuthenticationFilter(jwtProvider, mockUserSessionRegistry);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("Authorization 헤더가 없을 때")
  class NoAuthorizationHeader {

    @Test
    @DisplayName("인증을 시도하지 않고 다음 필터로 넘어간다")
    void doFilterInternal_noHeader_skipsAuthenticationAndContinuesChain() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, mockFilterChain);

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      verify(mockFilterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("유효한 access 토큰이 있을 때")
  class ValidAccessToken {

    @Test
    @DisplayName("SecurityContext에 UserPrincipal과 권한을 설정한다")
    void doFilterInternal_validToken_setsAuthentication() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID sid = UUID.randomUUID();
      String token = jwtProvider.createAccessToken(userId, "USER", sid, Instant.now());

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + token);
      MockHttpServletResponse response = new MockHttpServletResponse();

      given(mockUserSessionRegistry.verifyLoginSession(userId, sid))
          .willReturn(new UserSession(sid, UUID.randomUUID(), Instant.now()));

      // when
      filter.doFilterInternal(request, response, mockFilterChain);

      // then
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();

      UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
      assertThat(principal.userId()).isEqualTo(userId);
      assertThat(principal.role()).isEqualTo("USER");
      assertThat(authentication.getAuthorities())
          .extracting(Object::toString)
          .containsExactly("USER");
      assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
      verify(mockFilterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("access 토큰이 만료됐을 때")
  class ExpiredAccessToken {

    @Test
    @DisplayName("인증을 설정하지 않고, X-Token-Expired 헤더를 true로 설정한 뒤 다음 필터로 넘어간다")
    void doFilterInternal_expiredToken_setsHeaderAndContinuesChain() throws Exception {
      // given: 발급 시각을 과거로 잡아 이미 만료된 토큰을 만든다
      String expiredToken = jwtProvider.createAccessToken(
          UUID.randomUUID(), "USER", UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS));

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + expiredToken);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilterInternal(request, response, mockFilterChain);

      // then
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(response.getHeader("X-Token-Expired")).isEqualTo("true");
      verify(mockFilterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("access 토큰이 유효하지 않을 때")
  class InvalidAccessToken {

    @Test
    @DisplayName("인증을 설정하지 않고, X-Token-Expired 헤더 없이 다음 필터로 넘어간다")
    void doFilterInternal_invalidToken_doesNotSetHeaderAndContinuesChain() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer not-a-valid-jwt");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, mockFilterChain);

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(response.getHeader("X-Token-Expired")).isNull();
      verify(mockFilterChain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("세션이 무효화됐을 때 (다른 기기 재로그인 등으로 단일 로그인 정책에 의해 걸러짐)")
  class InvalidatedSession {

    @Test
    @DisplayName("인증을 설정하지 않고, X-Token-Expired 헤더 없이 다음 필터로 넘어간다")
    void doFilterInternal_sessionInvalidated_continuesChainWithoutAuthentication()
        throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID sid = UUID.randomUUID();
      String token = jwtProvider.createAccessToken(userId, "USER", sid, Instant.now());

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + token);
      MockHttpServletResponse response = new MockHttpServletResponse();

      given(mockUserSessionRegistry.verifyLoginSession(userId, sid))
          .willThrow(UserSessionExpiredException.withNone());

      // when
      filter.doFilterInternal(request, response, mockFilterChain);

      // then
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(response.getHeader("X-Token-Expired")).isNull();
      verify(mockFilterChain).doFilter(request, response);
    }
  }

}
