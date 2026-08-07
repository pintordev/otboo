package com.sprint.mission.otboo.security.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ErrorResponseWriter")
class ErrorResponseWriterTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Nested
  @DisplayName("에러 응답 작성 (write)")
  class Write {

    @Test
    @DisplayName("응답 상태코드/콘텐츠타입/인코딩을 지정한 값으로 설정한다")
    void 응답_상태코드_콘텐츠타입_인코딩을_지정한_값으로_설정한다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      BadCredentialsException exception = new BadCredentialsException("자격 증명 실패");

      // when
      ErrorResponseWriter.write(response, jsonMapper, HttpStatus.UNAUTHORIZED, exception, "인증이 필요합니다.");

      // then
      assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
      assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
      assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    }

    @Test
    @DisplayName("응답 바디에 예외 클래스 이름과 메시지를 JSON으로 담는다")
    void 응답_바디에_예외_클래스_이름과_메시지를_JSON으로_담는다() throws Exception {
      // given
      MockHttpServletResponse response = new MockHttpServletResponse();
      BadCredentialsException exception = new BadCredentialsException("자격 증명 실패");

      // when
      ErrorResponseWriter.write(response, jsonMapper, HttpStatus.UNAUTHORIZED, exception, "인증이 필요합니다.");

      // then
      String body = response.getContentAsString();
      assertThat(body).contains("\"exceptionName\":\"BadCredentialsException\"");
      assertThat(body).contains("\"message\":\"인증이 필요합니다.\"");
      assertThat(body).contains("\"details\":{}");
    }
  }
}
