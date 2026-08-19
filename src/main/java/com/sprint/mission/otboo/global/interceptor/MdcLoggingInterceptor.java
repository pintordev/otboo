package com.sprint.mission.otboo.global.interceptor;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MdcLoggingInterceptor implements HandlerInterceptor {

  private static final String HEADER_NAME = "Otboo-Request-Id";
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[a-zA-Z0-9-]{1,64}$");

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    if (MDC.get("requestId") == null) {
      String requestId = resolveRequestId(request);
      MDC.put("requestId", requestId);
      MDC.put("method", request.getMethod());
      MDC.put("url", request.getRequestURI());
      MDC.put("clientIp", resolveClientIp(request));
    }
    putTraceIdIfPresent();
    response.setHeader(HEADER_NAME, MDC.get("requestId"));
    return true;
  }

  private String resolveRequestId(HttpServletRequest request) {
    String inbound = request.getHeader(HEADER_NAME);
    return isValid(inbound) ? inbound : UUID.randomUUID().toString();
  }

  private void putTraceIdIfPresent() {
    Span span = Span.current();
    if (span.getSpanContext().isValid()) {
      MDC.put("traceId", span.getSpanContext().getTraceId());
    }
  }

  private boolean isValid(String value) {
    return value != null && SAFE_REQUEST_ID.matcher(value).matches();
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) {
    MDC.clear();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}