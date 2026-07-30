package com.sprint.mission.otboo.domain.authuser.auth.controller.api;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증", description = "인증 관련 API")
public interface AuthApi {

  @Operation(summary = "로그아웃", description = "로그아웃합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
      @ApiResponse(responseCode = "500", description = "Redis 장애")
  })
  ResponseEntity<Void> signOut(UserPrincipal principal, HttpServletResponse response);

  @Operation(summary = "로그인", description = "이메일/비밀번호 기반 로그인 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "로그인 성공"),
      @ApiResponse(responseCode = "400", description = "로그인 요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치"),
      @ApiResponse(responseCode = "403", description = "계정 잠김")
  })
  ResponseEntity<JwtDto> signIn(SignInRequest request, HttpServletResponse response);

  @Operation(summary = "임시 비밀번호 발급", description = "가입된 이메일로 임시 비밀번호를 발급해 메일로 전송합니다. 계정 존재 여부를 노출하지 않기 위해 이메일이 존재하지 않아도 항상 200을 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "요청 접수 성공 (가입된 이메일인 경우에만 실제로 임시 비밀번호가 발급/발송됨)"),
      @ApiResponse(responseCode = "400", description = "이메일이 비어있거나 형식이 올바르지 않음"),
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
}
