package com.sprint.mission.otboo.global.exception;

import com.sprint.mission.otboo.global.dto.ErrorResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[{}] {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(e.getClass().getSimpleName(), "요청한 리소스를 찾을 수 없습니다.", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[{}] {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(new ErrorResponse(e.getClass().getSimpleName(), "지원하지 않는 HTTP 메서드입니다.", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[{}] {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(e.getClass().getSimpleName(), "요청 본문을 읽을 수 없습니다.", null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
        Map<String, Object> details = Map.of("header", e.getHeaderName());
        log.warn("[{}] {}", e.getClass().getSimpleName(), details);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(e.getClass().getSimpleName(), "필수 헤더가 누락되었습니다.", details));
    }
}