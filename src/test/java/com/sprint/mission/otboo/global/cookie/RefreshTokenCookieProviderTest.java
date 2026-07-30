package com.sprint.mission.otboo.global.cookie;

import com.sprint.mission.otboo.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieProviderTest {

  private static final JwtProperties JWT_PROPERTIES =
      new JwtProperties("access-secret", "refresh-secret", 15L, 14L);

  private RefreshTokenCookieProvider refreshTokenCookieProvider;

  @BeforeEach
  void setUp() {
    refreshTokenCookieProvider = new RefreshTokenCookieProvider(JWT_PROPERTIES,
        new CookieProperties(true));
  }

  @Nested
  @DisplayName("attach")
  class Attach {

    @Test
    @DisplayName("refreshTokenExpirationDays만큼의 Max-Age와 토큰 값을 가진 쿠키를 응답에 추가한다")
    void attach_setsCookieWithTokenAndConfiguredMaxAge() {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      refreshTokenCookieProvider.attach(response, "refresh-token-value");

      // then
      String setCookieHeader = response.getHeader("Set-Cookie");
      assertThat(setCookieHeader).contains("REFRESH_TOKEN=refresh-token-value");
      assertThat(setCookieHeader).contains("Max-Age=" + Duration.ofDays(14).toSeconds());
      assertThat(setCookieHeader).contains("HttpOnly");
      assertThat(setCookieHeader).contains("Secure");
      assertThat(setCookieHeader).contains("SameSite=Strict");
      assertThat(setCookieHeader).contains("Path=/api/auth");
    }

    @Test
    @DisplayName("cookieProperties.secure()가 false면 Secure 속성이 붙지 않는다")
    void attach_secureFalse_omitsSecureAttribute() {
      // given
      RefreshTokenCookieProvider insecureProvider =
          new RefreshTokenCookieProvider(JWT_PROPERTIES, new CookieProperties(false));
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      insecureProvider.attach(response, "refresh-token-value");

      // then
      assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
    }
  }

  @Nested
  @DisplayName("clear")
  class Clear {

    @Test
    @DisplayName("빈 값과 Max-Age=0으로 쿠키를 무효화한다")
    void clear_setsEmptyValueAndZeroMaxAge() {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      refreshTokenCookieProvider.clear(response);

      // then
      String setCookieHeader = response.getHeader("Set-Cookie");
      assertThat(setCookieHeader).contains("REFRESH_TOKEN=");
      assertThat(setCookieHeader).contains("Max-Age=0");
    }
  }
}
