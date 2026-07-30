package com.sprint.mission.otboo.global.exception;

import tools.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.global.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Collections;

public final class ErrorResponseWriter {

  private ErrorResponseWriter() {
  }

  public static void write(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      HttpStatus status,
      Exception exception,
      String message
  ) throws IOException {

    ErrorResponse body = new ErrorResponse(exception.getClass().getSimpleName(), message,
        Collections.emptyMap());

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
