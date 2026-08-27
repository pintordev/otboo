package com.sprint.mission.otboo.security.oauth2.handler;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.exception.LoginRequiredException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.OAuth2FailureCode;
import com.sprint.mission.otboo.domain.authuser.auth.service.OAuth2SignInService;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.OAuth2Provider;
import com.sprint.mission.otboo.security.cookie.provider.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.security.oauth2.OAuth2RedirectSupport;
import com.sprint.mission.otboo.security.oauth2.properties.OAuth2Properties;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.TokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionException;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final String LINK_SUFFIX = "-link";

  private final OAuth2SignInService oAuth2SignInService;
  private final RefreshTokenCookieProvider refreshTokenCookieProvider;
  private final OAuth2Properties oAuth2Properties;
  private final TokenProvider tokenProvider;
  private final UserSessionRegistry userSessionRegistry;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) throws IOException {

    OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
    OAuth2User oAuth2User = token.getPrincipal();
    String registrationId = token.getAuthorizedClientRegistrationId(); // google | google-link | kakao | kakao-link

    boolean explicitLink = registrationId.endsWith(LINK_SUFFIX);
    String baseProvider = explicitLink
        ? registrationId.substring(0, registrationId.length() - LINK_SUFFIX.length())
        : registrationId;

    try {
      OAuth2Identity identity = resolveIdentity(baseProvider, oAuth2User);
      // 연동 전용 진입점일 때만 쿠키를 확인한다 - 로그인 화면 쪽은 쿠키를 아예 안 본다.
      UUID explicitLinkUserId = explicitLink ? resolveLoggedInUserId(request) : null;

      SignInDto result = oAuth2SignInService.signIn(
          identity.provider(), identity.providerId(), identity.email(), identity.name(),
          explicitLinkUserId);

      refreshTokenCookieProvider.attach(response, result.refreshToken());
      response.sendRedirect(oAuth2Properties.successRedirectUri());
    } catch (RuntimeException e) {
      String errorMessage = (e instanceof OAuth2FailureCode failureCode)
          ? failureCode.errorCode()
          : "oauth_processing_failed";
      log.warn("OAuth2 로그인 처리 실패: provider={}, reason={}", registrationId, errorMessage, e);
      OAuth2RedirectSupport.redirectWithError(response, oAuth2Properties.failureRedirectUri(),
          errorMessage);
    }
  }

  private UUID resolveLoggedInUserId(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, RefreshTokenCookieProvider.REFRESH_TOKEN);
    String refreshToken = cookie != null ? cookie.getValue() : null;

    if (!StringUtils.hasText(refreshToken)) {
      throw LoginRequiredException.withNone();
    }
    try {
      RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken);
      userSessionRegistry.verifyUserSession(claims.userId(), claims.sessionId());
      return claims.userId();
    } catch (TokenException | UserSessionException e) {
      throw LoginRequiredException.withCause(e);
    }
  }

  private OAuth2Identity resolveIdentity(String provider, OAuth2User oAuth2User) {
    return switch (provider) {
      case "google" -> resolveGoogleIdentity(oAuth2User);
      case "kakao" -> resolveKakaoIdentity(oAuth2User);
      default -> throw new IllegalStateException("지원하지 않는 provider: " + provider);
    };
  }

  private OAuth2Identity resolveGoogleIdentity(OAuth2User oAuth2User) {
    String providerId = oAuth2User.getAttribute("sub");
    String name = oAuth2User.getAttribute("name");
    Boolean emailVerified = oAuth2User.getAttribute("email_verified");
    String rawEmail = oAuth2User.getAttribute("email");
    String email = Boolean.TRUE.equals(emailVerified) && rawEmail != null
        ? rawEmail
        : name + "_" + providerId + "@google.com";
    return new OAuth2Identity(OAuth2Provider.GOOGLE, providerId, email, name);
  }

  // 카카오 id는 Long으로 내려오는데, String.valueOf(genericMethod())로 바로 넘기면
  // char[] 오버로드로 잘못 해석될 수 있어서(Object 변수로 한 번 받아서 넘겨야 한다.
  private OAuth2Identity resolveKakaoIdentity(OAuth2User oAuth2User) {
    Object rawId = oAuth2User.getAttribute("id");
    String providerId = String.valueOf(rawId);

    Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
    Map<String, Object> profile = kakaoAccount != null
        ? (Map<String, Object>) kakaoAccount.get("profile")
        : null;
    String nickname = profile != null && profile.get("nickname") != null
        ? (String) profile.get("nickname")
        : "kakao-user";

    String email = resolveKakaoEmail(kakaoAccount, nickname, providerId);
    return new OAuth2Identity(OAuth2Provider.KAKAO, providerId, email, nickname);
  }

  // 카카오는 이메일 제공에 동의 안 했거나 인증되지 않은 이메일이면 kakao_account.email
  // 자체가 없거나 신뢰할 수 없다. 이 경우 회원가입은 시켜야 하니 유일성이 보장되는
  // providerId로 가상 이메일을 만든다.
  private String resolveKakaoEmail(Map<String, Object> kakaoAccount, String nickname,
      String providerId) {
    if (kakaoAccount != null) {
      Boolean emailVerified = (Boolean) kakaoAccount.get("is_email_verified");
      Object email = kakaoAccount.get("email");
      if (Boolean.TRUE.equals(emailVerified) && email != null) {
        return (String) email;
      }
    }
    return nickname + "_" + providerId + "@kakao.com";
  }

  private record OAuth2Identity(
      OAuth2Provider provider,
      String providerId,
      String email,
      String name
  ) {

  }
}
