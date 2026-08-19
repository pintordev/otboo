package com.sprint.mission.otboo.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcLoggingInterceptorTest {

  private static final String HEADER_NAME = "Otboo-Request-Id";

  private final MdcLoggingInterceptor interceptor = new MdcLoggingInterceptor();

  @Nested
  @DisplayName("인바운드 헤더가 유효한 경우")
  class ValidInboundHeader {

    @Test
    @DisplayName("인바운드 Otboo-Request-ID 값을 그대로 MDC와 응답 헤더에 사용한다")
    void 인바운드_헤더값을_그대로_사용한다() {
      // given
      String inboundRequestId = "6cd96a12908e457ab9961f8e20c99016";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER_NAME, inboundRequestId);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      interceptor.preHandle(request, response, new Object());

      // then
      assertThat(MDC.get("requestId")).isEqualTo(inboundRequestId);
      assertThat(response.getHeader(HEADER_NAME)).isEqualTo(inboundRequestId);
    }
  }
}