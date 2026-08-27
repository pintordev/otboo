package com.sprint.mission.otboo.security.oauth2.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.service.OAuth2SignInService;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.OAuth2Provider;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.security.cookie.provider.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.security.oauth2.properties.OAuth2Properties;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginSuccessHandler")
class OAuth2LoginSuccessHandlerTest {

  private static final String SUCCESS_URI = "http://localhost:8080/#/";
  private static final String FAILURE_URI = "http://localhost:8080/#/sign-in";

  @Mock
  private OAuth2SignInService oAuth2SignInService;

  @Mock
  private RefreshTokenCookieProvider refreshTokenCookieProvider;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  private OAuth2LoginSuccessHandler successHandler;

  @BeforeEach
  void setUp() {
    OAuth2Properties oAuth2Properties = new OAuth2Properties(SUCCESS_URI, FAILURE_URI);
    successHandler = new OAuth2LoginSuccessHandler(
        oAuth2SignInService, refreshTokenCookieProvider, oAuth2Properties, tokenProvider,
        userSessionRegistry);
  }

  private OAuth2AuthenticationToken googleToken(String sub, String email, String name,
      String registrationId) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", sub);
    attributes.put("email", email);
    attributes.put("email_verified", true);
    attributes.put("name", name);
    OAuth2User oAuth2User = new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
    return new OAuth2AuthenticationToken(oAuth2User, oAuth2User.getAuthorities(), registrationId);
  }

  private OAuth2AuthenticationToken kakaoToken(Long id, Map<String, Object> kakaoAccount,
      String registrationId) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("id", id);
    if (kakaoAccount != null) {
      attributes.put("kakao_account", kakaoAccount);
    }
    OAuth2User oAuth2User = new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
    return new OAuth2AuthenticationToken(oAuth2User, oAuth2User.getAuthorities(), registrationId);
  }

  private Map<String, Object> kakaoAccount(boolean emailVerified, String email, String nickname) {
    Map<String, Object> profile = new HashMap<>();
    profile.put("nickname", nickname);
    Map<String, Object> account = new HashMap<>();
    account.put("profile", profile);
    account.put("is_email_verified", emailVerified);
    if (email != null) {
      account.put("email", email);
    }
    return account;
  }

  private SignInDto signInDtoOf(String email, String name) {
    UserDto userDto = new UserDto(UUID.randomUUID(), Instant.now(), email, name, Role.USER, false);
    return new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token");
  }

  @Nested
  @DisplayName("구글 로그인")
  class GoogleLogin {

    @Test
    @DisplayName("구글 로그인이 성공하면 리프레시 토큰 쿠키를 설정하고 성공 URI로 리다이렉트한다")
    void 구글_로그인이_성공하면_리프레시_토큰_쿠키를_설정하고_성공_URI로_리다이렉트한다() throws Exception {
      // given
      OAuth2AuthenticationToken token = googleToken("google-sub-1", "hong@gmail.com", "홍길동",
          "google");
      SignInDto signInDto = signInDtoOf("hong@gmail.com", "홍길동");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-sub-1", "hong@gmail.com", "홍길동", null))
          .willReturn(signInDto);

      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(request, response, token);

      // then
      verify(refreshTokenCookieProvider).attach(response, signInDto.refreshToken());
      assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_URI);
    }

    @Test
    @DisplayName("지원하지 않는 provider면 oauth_processing_failed로 리다이렉트한다")
    void 지원하지_않는_provider면_oauth_processing_failed로_리다이렉트한다() throws Exception {
      // given
      OAuth2AuthenticationToken token = googleToken("sub-1", "hong@gmail.com", "홍길동", "naver");
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);

      // then
      assertThat(response.getRedirectedUrl()).contains("error_message=oauth_processing_failed");
    }
  }

  @Nested
  @DisplayName("카카오 로그인")
  class KakaoLogin {

    @Test
    @DisplayName("카카오 id가 숫자여도 문자열로 변환해 SignInService에 전달한다")
    void 카카오_id가_숫자여도_문자열로_변환해_SignInService에_전달한다() throws Exception {
      // given
      Map<String, Object> kakaoAccount = kakaoAccount(true, "hong@gmail.com", "홍길동");
      OAuth2AuthenticationToken token = kakaoToken(123456789L, kakaoAccount, "kakao");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "123456789", "hong@gmail.com", "홍길동", null))
          .willReturn(signInDtoOf("hong@gmail.com", "홍길동"));

      // when
      successHandler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), token);

      // then
      verify(oAuth2SignInService).signIn(
          OAuth2Provider.KAKAO, "123456789", "hong@gmail.com", "홍길동", null);
    }

    @Test
    @DisplayName("카카오 이메일이 인증되지 않은 경우 가상 이메일을 생성해 사용한다")
    void 카카오_이메일이_인증되지_않은_경우_가상_이메일을_생성해_사용한다() throws Exception {
      // given
      Map<String, Object> kakaoAccount = kakaoAccount(false, "hong@gmail.com", "길동이");
      OAuth2AuthenticationToken token = kakaoToken(987654321L, kakaoAccount, "kakao");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "987654321", "길동이_987654321@kakao.com", "길동이", null))
          .willReturn(signInDtoOf("길동이_987654321@kakao.com", "길동이"));

      // when
      successHandler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), token);

      // then
      verify(oAuth2SignInService).signIn(
          OAuth2Provider.KAKAO, "987654321", "길동이_987654321@kakao.com", "길동이", null);
    }

    @Test
    @DisplayName("카카오 계정 정보가 없으면 기본 닉네임으로 가상 이메일을 생성한다")
    void 카카오_계정_정보가_없으면_기본_닉네임으로_가상_이메일을_생성한다() throws Exception {
      // given
      OAuth2AuthenticationToken token = kakaoToken(111L, null, "kakao");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "111", "kakao-user_111@kakao.com", "kakao-user", null))
          .willReturn(signInDtoOf("kakao-user_111@kakao.com", "kakao-user"));

      // when
      successHandler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), token);

      // then
      verify(oAuth2SignInService).signIn(
          OAuth2Provider.KAKAO, "111", "kakao-user_111@kakao.com", "kakao-user", null);
    }

    @Test
    @DisplayName("카카오 이메일이 인증됐지만 이메일 값 자체가 없으면 가상 이메일을 사용한다")
    void 카카오_이메일이_인증됐지만_이메일_값_자체가_없으면_가상_이메일을_사용한다() throws Exception {
      // given
      Map<String, Object> kakaoAccount = kakaoAccount(true, null, "길동이");
      OAuth2AuthenticationToken token = kakaoToken(555L, kakaoAccount, "kakao");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "555", "길동이_555@kakao.com", "길동이", null))
          .willReturn(signInDtoOf("길동이_555@kakao.com", "길동이"));

      // when
      successHandler.onAuthenticationSuccess(
          new MockHttpServletRequest(), new MockHttpServletResponse(), token);

      // then
      verify(oAuth2SignInService).signIn(
          OAuth2Provider.KAKAO, "555", "길동이_555@kakao.com", "길동이", null);
    }
  }

  @Nested
  @DisplayName("계정 연동 (-link 진입점)")
  class ExplicitLink {

    @Test
    @DisplayName("REFRESH_TOKEN 쿠키가 유효하고 세션이 살아있으면 해당 사용자 ID로 연동을 요청한다")
    void REFRESH_TOKEN_쿠키가_유효하고_세션이_살아있으면_해당_사용자_ID로_연동을_요청한다() throws Exception {
      // given
      UUID loggedInUserId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      given(tokenProvider.parseRefreshToken("valid-refresh-token"))
          .willReturn(new RefreshTokenClaims(loggedInUserId, sessionId, UUID.randomUUID()));
      given(userSessionRegistry.verifyUserSession(loggedInUserId, sessionId))
          .willReturn(UserSession.issue(loggedInUserId, Instant.now()));

      OAuth2AuthenticationToken token =
          googleToken("google-sub-2", "hong@gmail.com", "홍길동", "google-link");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-sub-2", "hong@gmail.com", "홍길동", loggedInUserId))
          .willReturn(signInDtoOf("hong@gmail.com", "홍길동"));

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(
          new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "valid-refresh-token"));

      // when
      successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), token);

      // then
      verify(oAuth2SignInService).signIn(
          OAuth2Provider.GOOGLE, "google-sub-2", "hong@gmail.com", "홍길동", loggedInUserId);
    }

    @Test
    @DisplayName("REFRESH_TOKEN이 유효해도 세션이 회수됐으면 login_required 에러로 실패 URI로 리다이렉트한다")
    void REFRESH_TOKEN이_유효해도_세션이_회수됐으면_login_required_에러로_실패_URI로_리다이렉트한다() throws Exception {
      // given
      UUID loggedInUserId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      given(tokenProvider.parseRefreshToken("revoked-session-token"))
          .willReturn(new RefreshTokenClaims(loggedInUserId, sessionId, UUID.randomUUID()));
      given(userSessionRegistry.verifyUserSession(loggedInUserId, sessionId))
          .willThrow(UserSessionExpiredException.withNone());

      OAuth2AuthenticationToken token =
          googleToken("google-sub-7", "hong@gmail.com", "홍길동", "google-link");
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(
          new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "revoked-session-token"));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(request, response, token);

      // then
      assertThat(response.getRedirectedUrl()).contains("error_message=login_required");
      verify(oAuth2SignInService, never()).signIn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("REFRESH_TOKEN 쿠키가 없으면 login_required 에러로 실패 URI로 리다이렉트한다")
    void REFRESH_TOKEN_쿠키가_없으면_login_required_에러로_실패_URI로_리다이렉트한다() throws Exception {
      // given
      OAuth2AuthenticationToken token =
          googleToken("google-sub-3", "hong@gmail.com", "홍길동", "google-link");
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(request, response, token);

      // then
      assertThat(response.getRedirectedUrl())
          .startsWith(FAILURE_URI)
          .contains("error_message=login_required");
      verify(oAuth2SignInService, never()).signIn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("REFRESH_TOKEN 쿠키 값이 유효하지 않으면 login_required 에러로 실패 URI로 리다이렉트한다")
    void REFRESH_TOKEN_쿠키_값이_유효하지_않으면_login_required_에러로_실패_URI로_리다이렉트한다() throws Exception {
      // given
      given(tokenProvider.parseRefreshToken("invalid-token"))
          .willThrow(InvalidRefreshTokenException.withNone());

      OAuth2AuthenticationToken token =
          googleToken("google-sub-4", "hong@gmail.com", "홍길동", "google-link");
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new Cookie(RefreshTokenCookieProvider.REFRESH_TOKEN, "invalid-token"));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(request, response, token);

      // then
      assertThat(response.getRedirectedUrl()).contains("error_message=login_required");
      verify(oAuth2SignInService, never()).signIn(any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("로그인/연동 실패 처리")
  class FailureHandling {

    @Test
    @DisplayName("OAuth2FailureCode를 구현한 예외면 해당 에러 코드로 실패 URI로 리다이렉트한다")
    void OAuth2FailureCode를_구현한_예외면_해당_에러_코드로_실패_URI로_리다이렉트한다() throws Exception {
      // given
      OAuth2AuthenticationToken token =
          googleToken("google-sub-5", "locked@gmail.com", "홍길동", "google");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-sub-5", "locked@gmail.com", "홍길동", null))
          .willThrow(AccountLockedException.withNone());

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);

      // then
      assertThat(response.getRedirectedUrl()).contains("error_message=account_locked");
      verify(refreshTokenCookieProvider, never()).attach(any(), any());
    }

    @Test
    @DisplayName("OAuth2FailureCode가 아닌 예외면 oauth_processing_failed로 리다이렉트한다")
    void OAuth2FailureCode가_아닌_예외면_oauth_processing_failed로_리다이렉트한다() throws Exception {
      // given
      OAuth2AuthenticationToken token =
          googleToken("google-sub-6", "hong@gmail.com", "홍길동", "google");
      given(oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-sub-6", "hong@gmail.com", "홍길동", null))
          .willThrow(new IllegalStateException("예상치 못한 오류"));

      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);

      // then
      assertThat(response.getRedirectedUrl()).contains("error_message=oauth_processing_failed");
    }
  }
}
