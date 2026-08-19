package com.sprint.mission.otboo.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcLoggingInterceptorTest {

  private static final String HEADER_NAME = "Otboo-Request-Id";

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private final MdcLoggingInterceptor interceptor = new MdcLoggingInterceptor();

  @BeforeEach
  void clearMdcBeforeEach() {
    MDC.clear();
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  @DisplayName("인바운드 헤더가 유효한 경우")
  class ValidInboundHeader {

    @Test
    @DisplayName("인바운드 Otboo-Request-Id 값을 그대로 MDC와 응답 헤더에 사용한다")
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

  @Nested
  @DisplayName("인바운드 헤더가 없는 경우")
  class NoInboundHeader {

    @Test
    @DisplayName("기존처럼 새 UUID를 생성해 MDC와 응답 헤더에 사용한다")
    void 헤더가_없으면_새로_생성한다() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      interceptor.preHandle(request, response, new Object());

      // then
      String requestId = MDC.get("requestId");
      assertThat(requestId).isNotBlank();
      assertThat(UUID.fromString(requestId)).isNotNull();
      assertThat(response.getHeader(HEADER_NAME)).isEqualTo(requestId);
    }
  }

  @Nested
  @DisplayName("인바운드 헤더 형식이 무효한 경우")
  class InvalidInboundHeader {

    @Test
    @DisplayName("개행 문자가 포함된 값은 버리고 새로 생성한다")
    void 개행_포함_값은_무시하고_새로_생성한다() {
      // given
      String malicious = "abc\ninjected-log-line";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER_NAME, malicious);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      interceptor.preHandle(request, response, new Object());

      // then
      String requestId = MDC.get("requestId");
      assertThat(requestId).isNotEqualTo(malicious);
      assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    @DisplayName("길이 상한(64자)을 넘는 값은 버리고 새로 생성한다")
    void 길이_상한_초과_값은_무시하고_새로_생성한다() {
      // given
      String tooLong = "a".repeat(65);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER_NAME, tooLong);
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      interceptor.preHandle(request, response, new Object());

      // then
      String requestId = MDC.get("requestId");
      assertThat(requestId).isNotEqualTo(tooLong);
      assertThat(UUID.fromString(requestId)).isNotNull();
    }
  }

  @Nested
  @DisplayName("OTel trace id 연동")
  class TraceIdIntegration {

    private final Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");

    @Test
    @DisplayName("활성 span이 있으면 traceId를 MDC에 추가한다")
    void 활성_span이_있으면_traceId를_MDC에_추가한다() {
      // given
      Span span = tracer.spanBuilder("test-span").startSpan();

      // when & then
      try (Scope scope = span.makeCurrent()) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("traceId")).isEqualTo(span.getSpanContext().getTraceId());
      } finally {
        span.end();
      }
    }

    @Test
    @DisplayName("활성 span이 없으면 traceId를 MDC에 추가하지 않는다")
    void 활성_span이_없으면_traceId를_MDC에_추가하지_않는다() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      interceptor.preHandle(request, response, new Object());

      // then
      assertThat(MDC.get("traceId")).isNull();
    }
  }
}