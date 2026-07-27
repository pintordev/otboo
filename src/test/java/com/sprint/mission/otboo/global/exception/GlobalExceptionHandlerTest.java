package com.sprint.mission.otboo.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FakeController.class)
class GlobalExceptionHandlerTest {

  @Autowired
  private MockMvc mockMvc;

  @RestController
  @RequestMapping("/test")
  static class FakeController {

    @GetMapping("/ping")
    public void ping() {
    }

    @GetMapping("/unexpected")
    public void unexpected() {
      throw new IllegalStateException("예상하지 못한 오류");
    }

    @GetMapping("/otboo-exception")
    public void otbooException() {
      throw new FakeNotFoundException();
    }

    @PostMapping("/body")
    public void body(@Valid @RequestBody FakeRequest request) {
    }

    @GetMapping("/header")
    public void header(@RequestHeader("X-Test-Header") String header) {
    }

    @GetMapping("/param")
    public void param(@RequestParam String name) {
    }

    @GetMapping("/type-mismatch")
    public void typeMismatch(@RequestParam UUID id) {
    }
  }

  record FakeRequest(@NotBlank String name) {

  }

  static class FakeNotFoundException extends OtbooException {

    FakeNotFoundException() {
      super(HttpStatus.NOT_FOUND, "찾을 수 없습니다", Map.of("id", "test-id"));
    }
  }

  @Nested
  @DisplayName("존재하지 않는 리소스 요청 시")
  class NoResourceFoundHandling {

    @Test
    @DisplayName("404를 반환한다")
    void _404를_반환한다() throws Exception {
      mockMvc.perform(get("/test/does-not-exist"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("NoResourceFoundException"));
    }
  }

  @Nested
  @DisplayName("지원하지 않는 HTTP 메서드로 요청 시")
  class MethodNotSupportedHandling {

    @Test
    @DisplayName("405를 반환한다")
    void _405를_반환한다() throws Exception {
      mockMvc.perform(post("/test/ping"))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.exceptionName").value("HttpRequestMethodNotSupportedException"));
    }
  }

  @Nested
  @DisplayName("요청 바디를 파싱할 수 없을 때")
  class MessageNotReadableHandling {

    @Test
    @DisplayName("400을 반환한다")
    void _400을_반환한다() throws Exception {
      mockMvc.perform(post("/test/body")
              .contentType(MediaType.APPLICATION_JSON)
              .content("not-json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("HttpMessageNotReadableException"));
    }
  }

  @Nested
  @DisplayName("필수 헤더가 없을 때")
  class MissingRequestHeaderHandling {

    @Test
    @DisplayName("400을 반환한다")
    void _400을_반환한다() throws Exception {
      mockMvc.perform(get("/test/header"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("MissingRequestHeaderException"));
    }
  }

  @Nested
  @DisplayName("필수 파라미터가 없을 때")
  class MissingRequestParamHandling {

    @Test
    @DisplayName("400을 반환한다")
    void _400을_반환한다() throws Exception {
      mockMvc.perform(get("/test/param"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("MissingServletRequestParameterException"));
    }
  }

  @Nested
  @DisplayName("파라미터 타입이 일치하지 않을 때")
  class TypeMismatchHandling {

    @Test
    @DisplayName("400을 반환한다")
    void _400을_반환한다() throws Exception {
      mockMvc.perform(get("/test/type-mismatch").param("id", "not-a-uuid"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("MethodArgumentTypeMismatchException"));
    }
  }

  @Nested
  @DisplayName("@Valid 검증에 실패했을 때")
  class ValidationHandling {

    @Test
    @DisplayName("400과 필드별 메시지를 반환한다")
    void _400과_필드별_메시지를_반환한다() throws Exception {
      mockMvc.perform(post("/test/body")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
          .andExpect(jsonPath("$.details.name").exists());
    }
  }

  @Nested
  @DisplayName("OtbooException이 발생했을 때")
  class OtbooExceptionHandling {

    @Test
    @DisplayName("예외의 상태 코드와 ErrorResponse를 반환한다")
    void 예외의_상태_코드와_ErrorResponse를_반환한다() throws Exception {
      mockMvc.perform(get("/test/otboo-exception"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("FakeNotFoundException"))
          .andExpect(jsonPath("$.message").value("찾을 수 없습니다"))
          .andExpect(jsonPath("$.details.id").value("test-id"));
    }
  }

  @Nested
  @DisplayName("예상하지 못한 예외가 발생했을 때")
  class UnexpectedExceptionHandling {

    @Test
    @DisplayName("500을 반환한다")
    void _500을_반환한다() throws Exception {
      mockMvc.perform(get("/test/unexpected"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.exceptionName").value("IllegalStateException"));
    }
  }
}