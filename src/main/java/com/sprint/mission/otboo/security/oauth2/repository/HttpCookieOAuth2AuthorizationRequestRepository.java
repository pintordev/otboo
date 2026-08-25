package com.sprint.mission.otboo.security.oauth2.repository;

import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  public static final String OAUTH2_AUTHORIZATION_REQUEST = "OAUTH2_AUTHORIZATION_REQUEST";
  private static final Duration COOKIE_EXPIRE = Duration.ofMinutes(3);

  private final RefreshTokenCookieProperties cookieProperties;
  private final JsonMapper jsonMapper;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST);
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return null;
    }
    return deserialize(cookie);
  }

  @Override
  public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request, HttpServletResponse response) {
    if (authorizationRequest == null) {
      removeAuthorizationRequest(request, response);
      return;
    }
    addCookie(response, serialize(authorizationRequest), COOKIE_EXPIRE);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
      HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    addCookie(response, "", Duration.ZERO);
    return authorizationRequest;
  }

  private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
    ResponseCookie cookie = ResponseCookie.from(OAUTH2_AUTHORIZATION_REQUEST, value)
        .httpOnly(true)
        .secure(cookieProperties.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
    byte[] json = jsonMapper.writeValueAsBytes(CookiePayload.from(authorizationRequest));
    return Base64.getUrlEncoder().encodeToString(json);
  }

  private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
    try {
      byte[] json = Base64.getUrlDecoder().decode(cookie.getValue());
      return jsonMapper.readValue(json, CookiePayload.class).toAuthorizationRequest();
    } catch (IllegalArgumentException | JacksonException e) {
      log.warn("OAuth2AuthorizationRequest 쿠키 역직렬화 실패", e);
      return null;
    }
  }

  private record CookiePayload(
      String authorizationUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalParameters,
      Map<String, Object> attributes
  ) {

    static CookiePayload from(OAuth2AuthorizationRequest authorizationRequest) {
      return new CookiePayload(
          authorizationRequest.getAuthorizationUri(),
          authorizationRequest.getClientId(),
          authorizationRequest.getRedirectUri(),
          authorizationRequest.getScopes(),
          authorizationRequest.getState(),
          authorizationRequest.getAdditionalParameters(),
          authorizationRequest.getAttributes()
      );
    }

    OAuth2AuthorizationRequest toAuthorizationRequest() {
      return OAuth2AuthorizationRequest.authorizationCode()
          .authorizationUri(authorizationUri)
          .clientId(clientId)
          .redirectUri(redirectUri)
          .scopes(scopes)
          .state(state)
          .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
          .attributes(attributes == null ? Map.of() : attributes)
          .build();
    }
  }
}
