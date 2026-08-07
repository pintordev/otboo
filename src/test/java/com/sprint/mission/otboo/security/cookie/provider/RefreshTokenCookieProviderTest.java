package com.sprint.mission.otboo.security.cookie.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RefreshTokenCookieProvider")
class RefreshTokenCookieProviderTest {

  @Nested
  @DisplayName("쿠키 발급 (attach)")
  class Attach {

    @Test
    @DisplayName("refreshToken을 HttpOnly/SameSite=Strict/지정된 경로의 쿠키로 응답 헤더에 담는다")
    void refreshToken을_HttpOnly_SameSite_Strict_지정된_경로의_쿠키로_응답_헤더에_담는다() {
      // given
      RefreshTokenCookieProperties properties =
          new RefreshTokenCookieProperties(Duration.ofDays(14), false);
      RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider(properties);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      provider.attach(response, "raw-refresh-token");

      // then
      String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookie).contains("REFRESH_TOKEN=raw-refresh-token");
      assertThat(setCookie).contains("HttpOnly");
      assertThat(setCookie).contains("SameSite=Strict");
      assertThat(setCookie).contains("Path=/api/auth");
      assertThat(setCookie).contains("Max-Age=" + Duration.ofDays(14).toSeconds());
    }

    @Test
    @DisplayName("secure=true로 설정되면 쿠키에 Secure 속성이 포함된다")
    void secure가_true로_설정되면_쿠키에_Secure_속성이_포함된다() {
      // given
      RefreshTokenCookieProperties properties =
          new RefreshTokenCookieProperties(Duration.ofDays(14), true);
      RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider(properties);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      provider.attach(response, "raw-refresh-token");

      // then
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    @DisplayName("secure=false로 설정되면 쿠키에 Secure 속성이 포함되지 않는다")
    void secure가_false로_설정되면_쿠키에_Secure_속성이_포함되지_않는다() {
      // given
      RefreshTokenCookieProperties properties =
          new RefreshTokenCookieProperties(Duration.ofDays(14), false);
      RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider(properties);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      provider.attach(response, "raw-refresh-token");

      // then
      assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }
  }

  @Nested
  @DisplayName("쿠키 삭제 (clear)")
  class Clear {

    @Test
    @DisplayName("값을 비우고 Max-Age를 0으로 설정해 즉시 만료시킨다")
    void 값을_비우고_MaxAge를_0으로_설정해_즉시_만료시킨다() {
      // given
      RefreshTokenCookieProperties properties =
          new RefreshTokenCookieProperties(Duration.ofDays(14), false);
      RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider(properties);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      provider.clear(response);

      // then
      String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookie).contains("REFRESH_TOKEN=");
      assertThat(setCookie).contains("Max-Age=0");
    }
  }
}
