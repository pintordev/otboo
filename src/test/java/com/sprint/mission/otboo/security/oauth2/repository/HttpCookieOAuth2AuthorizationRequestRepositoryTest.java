package com.sprint.mission.otboo.security.oauth2.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("HttpCookieOAuth2AuthorizationRequestRepository")
class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

  private static OAuth2AuthorizationRequest authorizationRequest() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://provider.example.com/oauth2/authorize")
        .clientId("client-id")
        .redirectUri("https://otboo.example.com/login/oauth2/code/google")
        .scopes(Set.of("email", "profile"))
        .state("state-value")
        .build();
  }

  private static HttpCookieOAuth2AuthorizationRequestRepository repository(boolean secure) {
    return new HttpCookieOAuth2AuthorizationRequestRepository(
        new RefreshTokenCookieProperties(Duration.ofDays(14), secure), new JsonMapper());
  }

  private static MockHttpServletRequest requestWithCookiesFrom(MockHttpServletResponse response) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(response.getCookies());
    return request;
  }

  @Nested
  @DisplayName("저장 (saveAuthorizationRequest)")
  class Save {

    @Test
    @DisplayName("authorizationRequest를_직렬화해_HttpOnly_SameSite_Lax_루트_경로의_쿠키로_응답_헤더에_담는다")
    void authorizationRequest를_직렬화해_HttpOnly_SameSite_Lax_루트_경로의_쿠키로_응답_헤더에_담는다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      repository.saveAuthorizationRequest(authorizationRequest(), request, response);

      // then
      String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookie).contains(
          HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST + "=");
      assertThat(setCookie).contains("HttpOnly");
      assertThat(setCookie).contains("SameSite=Lax");
      assertThat(setCookie).contains("Path=/");
    }

    @Test
    @DisplayName("secure가_true로_설정되면_쿠키에_Secure_속성이_포함된다")
    void secure가_true로_설정되면_쿠키에_Secure_속성이_포함된다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(true);
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      repository.saveAuthorizationRequest(authorizationRequest(), request, response);

      // then
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    @DisplayName("authorizationRequest가_null이면_쿠키를_즉시_만료시킨다")
    void authorizationRequest가_null이면_쿠키를_즉시_만료시킨다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      repository.saveAuthorizationRequest(null, request, response);

      // then
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }
  }

  @Nested
  @DisplayName("조회 (loadAuthorizationRequest)")
  class Load {

    @Test
    @DisplayName("저장된_쿠키를_역직렬화해_authorizationRequest를_반환한다")
    void 저장된_쿠키를_역직렬화해_authorizationRequest를_반환한다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletResponse saveResponse = new MockHttpServletResponse();
      OAuth2AuthorizationRequest original = authorizationRequest();
      repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
      MockHttpServletRequest loadRequest = requestWithCookiesFrom(saveResponse);

      // when
      OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

      // then
      assertThat(loaded).isNotNull();
      assertThat(loaded.getClientId()).isEqualTo(original.getClientId());
      assertThat(loaded.getState()).isEqualTo(original.getState());
      assertThat(loaded.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
      assertThat(loaded.getRedirectUri()).isEqualTo(original.getRedirectUri());
    }

    @Test
    @DisplayName("쿠키가_없으면_null을_반환한다")
    void 쿠키가_없으면_null을_반환한다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletRequest request = new MockHttpServletRequest();

      // when
      OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);

      // then
      assertThat(loaded).isNull();
    }

    @Test
    @DisplayName("쿠키_값이_손상되어_있으면_예외_없이_null을_반환한다")
    void 쿠키_값이_손상되어_있으면_예외_없이_null을_반환한다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new Cookie(
          HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST,
          "corrupted-value"));

      // when
      OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);

      // then
      assertThat(loaded).isNull();
    }
  }

  @Nested
  @DisplayName("삭제 (removeAuthorizationRequest)")
  class Remove {

    @Test
    @DisplayName("기존에_저장된_값을_반환하고_쿠키를_MaxAge_0으로_즉시_만료시킨다")
    void 기존에_저장된_값을_반환하고_쿠키를_MaxAge_0으로_즉시_만료시킨다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletResponse saveResponse = new MockHttpServletResponse();
      OAuth2AuthorizationRequest original = authorizationRequest();
      repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
      MockHttpServletRequest removeRequest = requestWithCookiesFrom(saveResponse);
      MockHttpServletResponse removeResponse = new MockHttpServletResponse();

      // when
      OAuth2AuthorizationRequest removed =
          repository.removeAuthorizationRequest(removeRequest, removeResponse);

      // then
      assertThat(removed).isNotNull();
      assertThat(removed.getState()).isEqualTo(original.getState());
      assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("저장된_쿠키가_없으면_null을_반환하고_쿠키를_즉시_만료시킨다")
    void 저장된_쿠키가_없으면_null을_반환하고_쿠키를_즉시_만료시킨다() {
      // given
      HttpCookieOAuth2AuthorizationRequestRepository repository = repository(false);
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, response);

      // then
      assertThat(removed).isNull();
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }
  }
}
