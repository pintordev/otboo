package com.sprint.mission.otboo.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @RestController
    @RequestMapping("/test")
    static class FakeController {

        @GetMapping("/ping")
        public void ping() {
        }
    }
}