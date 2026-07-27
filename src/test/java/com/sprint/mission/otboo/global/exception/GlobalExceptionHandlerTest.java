package com.sprint.mission.otboo.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

    @Nested
    @DisplayName("존재하지 않는 리소스 요청 시")
    class NoResourceFound_처리 {

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
    class MethodNotSupported_처리 {

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
    class MessageNotReadable_처리 {

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
    class MissingRequestHeader_처리 {

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
    class MissingRequestParam_처리 {

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
    class TypeMismatch_처리 {

        @Test
        @DisplayName("400을 반환한다")
        void _400을_반환한다() throws Exception {
            mockMvc.perform(get("/test/type-mismatch").param("id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentTypeMismatchException"));
        }
    }

    @RestController
    @RequestMapping("/test")
    static class FakeController {

        @GetMapping("/ping")
        public void ping() {
        }

        @PostMapping("/body")
        public void body(@RequestBody FakeRequest request) {
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

    record FakeRequest(String name) {

    }
}