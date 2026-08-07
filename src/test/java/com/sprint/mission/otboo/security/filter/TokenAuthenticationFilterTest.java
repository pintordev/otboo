package com.sprint.mission.otboo.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.security.details.UserPrincipal;
import com.sprint.mission.otboo.security.token.dto.AccessTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.ExpiredTokenException;
import com.sprint.mission.otboo.security.token.exception.business.InvalidAccessTokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.exception.business.RefreshTokenReusedException;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenAuthenticationFilter")
class TokenAuthenticationFilterTest {

  @InjectMocks
  private TokenAuthenticationFilter filter;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  @Mock
  private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest requestWithBearer(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (token != null) {
      request.addHeader("Authorization", "Bearer " + token);
    }
    return request;
  }

  @Nested
  @DisplayName("토큰이 없는 요청")
  class NoToken {

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증을 시도하지 않고 필터체인을 그대로 진행한다")
    void Authorization_헤더가_없으면_인증을_시도하지_않고_필터체인을_그대로_진행한다() throws Exception {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      verify(filterChain).doFilter(request, response);
      verify(tokenProvider, org.mockito.Mockito.never()).parseAccessToken(any());
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사가 없는 Authorization 헤더는 무시하고 필터체인을 그대로 진행한다")
    void Bearer_접두사가_없는_Authorization_헤더는_무시하고_필터체인을_그대로_진행한다() throws Exception {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Basic abcdef");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Nested
  @DisplayName("정상 토큰 인증")
  class ValidToken {

    @Test
    @DisplayName("유효한 토큰이면 SecurityContext에 인증 정보를 설정하고 필터체인을 진행한다")
    void 유효한_토큰이면_SecurityContext에_인증_정보를_설정하고_필터체인을_진행한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      AccessTokenClaims claims = new AccessTokenClaims(userId, sessionId, "ADMIN");
      given(tokenProvider.parseAccessToken("valid-token")).willReturn(claims);

      MockHttpServletRequest request = requestWithBearer("valid-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      verify(filterChain).doFilter(request, response);
      verify(userSessionRegistry).verifyUserSession(userId, sessionId);

      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();
      assertThat(authentication.getPrincipal()).isEqualTo(new UserPrincipal(userId, "ADMIN"));
      assertThat(authentication.getAuthorities())
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ADMIN");
    }
  }

  @Nested
  @DisplayName("만료/무효 토큰 처리")
  class InvalidToken {

    @Test
    @DisplayName("액세스 토큰이 만료되었으면 X-Token-Expired 헤더를 설정하고 필터체인은 계속 진행한다")
    void 액세스_토큰이_만료되었으면_XTokenExpired_헤더를_설정하고_필터체인은_계속_진행한다() throws Exception {
      // given
      given(tokenProvider.parseAccessToken("expired-token")).willThrow(ExpiredTokenException.withNone());
      MockHttpServletRequest request = requestWithBearer("expired-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      assertThat(response.getHeader("X-Token-Expired")).isEqualTo("true");
      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("세션이 만료되었으면 X-User-Session-Expired 헤더를 설정하고 필터체인은 계속 진행한다")
    void 세션이_만료되었으면_XUserSessionExpired_헤더를_설정하고_필터체인은_계속_진행한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      AccessTokenClaims claims = new AccessTokenClaims(userId, sessionId, "USER");
      given(tokenProvider.parseAccessToken("valid-token")).willReturn(claims);
      willThrow(UserSessionExpiredException.withNone())
          .given(userSessionRegistry).verifyUserSession(userId, sessionId);

      MockHttpServletRequest request = requestWithBearer("valid-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      assertThat(response.getHeader("X-User-Session-Expired")).isEqualTo("true");
      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("그 외 토큰 예외는 별도 헤더 없이 필터체인만 계속 진행한다")
    void 그_외_토큰_예외는_별도_헤더_없이_필터체인만_계속_진행한다() throws Exception {
      // given
      given(tokenProvider.parseAccessToken("malformed-token"))
          .willThrow(InvalidAccessTokenException.withNone());
      MockHttpServletRequest request = requestWithBearer("malformed-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      assertThat(response.getHeader("X-Token-Expired")).isNull();
      assertThat(response.getHeader("X-User-Session-Expired")).isNull();
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("그 외 세션 예외(재사용 감지 등)도 별도 헤더 없이 필터체인만 계속 진행한다")
    void 그_외_세션_예외도_별도_헤더_없이_필터체인만_계속_진행한다() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      AccessTokenClaims claims = new AccessTokenClaims(userId, sessionId, "USER");
      given(tokenProvider.parseAccessToken("valid-token")).willReturn(claims);
      willThrow(RefreshTokenReusedException.withNone())
          .given(userSessionRegistry).verifyUserSession(userId, sessionId);

      MockHttpServletRequest request = requestWithBearer("valid-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, filterChain);

      // then
      assertThat(response.getHeader("X-Token-Expired")).isNull();
      assertThat(response.getHeader("X-User-Session-Expired")).isNull();
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("예상하지 못한 런타임 예외가 발생해도 요청을 끊지 않고 필터체인을 계속 진행한다")
    void 예상하지_못한_런타임_예외가_발생해도_요청을_끊지_않고_필터체인을_계속_진행한다() throws Exception {
      // given: Redis 장애 등으로 세션 조회 자체가 실패하는 상황을 가정
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      AccessTokenClaims claims = new AccessTokenClaims(userId, sessionId, "USER");
      given(tokenProvider.parseAccessToken("valid-token")).willReturn(claims);
      willThrow(new RuntimeException("Redis 연결 실패"))
          .given(userSessionRegistry).verifyUserSession(userId, sessionId);

      MockHttpServletRequest request = requestWithBearer("valid-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when & then: 예외가 필터 밖으로 전파되지 않는다
      filter.doFilter(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }
}
