package com.sprint.mission.otboo.domain.authuser.auth.controller.api;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "인증", description = "인증 관련 API")
public interface AuthApi {

  @Operation(summary = "로그아웃", description = "로그아웃합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
      @ApiResponse(responseCode = "500", description = "Redis 장애")
  })
  ResponseEntity<Void> signOut(String refreshToken, HttpServletResponse response);

  @Operation(summary = "로그인", description = "이메일/비밀번호 기반 로그인 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "로그인 성공"),
      @ApiResponse(responseCode = "400", description = "로그인 요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치"),
      @ApiResponse(responseCode = "403", description = "계정 잠김")
  })
  ResponseEntity<JwtDto> signIn(SignInRequest request, HttpServletResponse response);

  @Operation(summary = "비밀번호 초기화", description = "임시 비밀번호로 초기화 후 이메일로 전송합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "비밀번호 초기화 성공"),
      @ApiResponse(responseCode = "400", description = "이메일이 비어있거나 형식이 올바르지 않음"),
      @ApiResponse(responseCode = "404", description = "비밀번호 초기화 실패(사용자 없음)"),
      @ApiResponse(responseCode = "500", description = "Redis 장애")
  })
  ResponseEntity<Void> resetPassword(ResetPasswordRequest request);

  @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰 쿠키를 이용해 액세스 토큰과 리프레시 토큰을 재발급합니다(Refresh Token Rotation).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "재발급 성공"),
      @ApiResponse(responseCode = "400", description = "리프레시 토큰 쿠키 누락"),
      @ApiResponse(responseCode = "401", description = "리프레시 토큰이 유효하지 않거나 만료됨 / 세션이 만료되었거나 다른 기기 로그인으로 무효화됨 / 토큰 재사용 탐지"),
      @ApiResponse(responseCode = "403", description = "계정 잠김"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  ResponseEntity<JwtDto> refresh(String refreshToken, HttpServletResponse response);

  @Operation(summary = "CSRF 토큰 발급", description = "CSRF 토큰을 발급해 쿠키(XSRF-TOKEN)에 저장합니다. 이후 상태를 변경하는 요청에는 이 쿠키 값을 X-XSRF-TOKEN 헤더에 담아 함께 보내야 합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "CSRF 토큰 발급 성공")
  })
  ResponseEntity<Void> csrfToken(@Parameter(hidden = true) CsrfToken csrfToken);
}
