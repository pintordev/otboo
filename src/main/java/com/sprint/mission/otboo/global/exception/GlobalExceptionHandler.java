package com.sprint.mission.otboo.global.exception;

import com.sprint.mission.otboo.global.dto.ErrorResponse;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
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

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestParam(
      MissingServletRequestParameterException e) {
    Map<String, Object> details = Map.of(e.getParameterName(), "필수 파라미터입니다.");
    log.warn("[{}] {}", e.getClass().getSimpleName(), details);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(e.getClass().getSimpleName(), "필수 파라미터가 누락되었습니다.", details));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    Map<String, Object> details = Map.of(
        e.getName(),
        e.getRequiredType() != null ? e.getRequiredType().getSimpleName() + " 타입이어야 합니다."
            : "잘못된 타입입니다."
    );
    log.warn("[{}] {}", e.getClass().getSimpleName(), details);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(e.getClass().getSimpleName(), "파라미터 타입이 올바르지 않습니다.", details));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
            (existing, replacement) -> existing
        ));
    log.warn("[{}] {}", e.getClass().getSimpleName(), details);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(e.getClass().getSimpleName(), "요청 값이 유효하지 않습니다.", details));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    log.warn("[{}] {}", e.getClass().getSimpleName(), e.getMessage());
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(e.getClass().getSimpleName(), "접근 권한이 없습니다.", null));
  }

  @ExceptionHandler(OtbooException.class)
  public ResponseEntity<ErrorResponse> handleOtbooException(OtbooException e) {
    log.warn("[{}] {}", e.getClass().getSimpleName(), e.getMessage());
    return ResponseEntity
        .status(e.getStatus())
        .body(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage(), e.getDetails()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("[{}] {}", e.getClass().getSimpleName(), e.getMessage(), e);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(e.getClass().getSimpleName(), "서버 내부 오류가 발생했습니다.", null));
  }
}