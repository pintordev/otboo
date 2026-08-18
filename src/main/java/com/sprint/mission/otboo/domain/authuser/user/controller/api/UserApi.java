package com.sprint.mission.otboo.domain.authuser.user.controller.api;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.ChangePasswordRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ProfileUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "프로필 관리", description = "프로필 관련 API")
public interface UserApi {

  @Operation(summary = "사용자 등록(회원가입)", description = "사용자 등록(회원가입) API")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "사용자 등록(회원가입) 성공"),
      @ApiResponse(responseCode = "400", description = "사용자 등록(회원가입) 실패"),
      @ApiResponse(responseCode = "409", description = "사용자 등록(회원가입) 이메일 중복")
  })
  ResponseEntity<UserDto> signUp(UserCreateRequest request);

  @Operation(summary = "프로필 조회", description = "본인 프로필을 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  ResponseEntity<ProfileDto> getProfile(UUID userId);

  @Operation(summary = "프로필 수정", description = "본인 프로필(이름, 성별, 생년월일, 위치, 체감온도 민감도)을 수정합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "프로필 수정 성공"),
      @ApiResponse(responseCode = "400", description = "요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "403", description = "본인 프로필만 수정 가능"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  ResponseEntity<ProfileDto> changeProfile(UUID userId, ProfileUpdateRequest request,
      MultipartFile image, UserPrincipal principal);

  @Operation(summary = "비밀번호 변경", description = "비밀번호를 변경합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공"),
      @ApiResponse(responseCode = "400", description = "요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "403", description = "본인 비밀번호만 변경 가능"),
      @ApiResponse(responseCode = "404", description = "비밀번호 변경 실패(사용자 없음)")
  })
  ResponseEntity<Void> changePassword(UUID userId, ChangePasswordRequest request,
      UserPrincipal principal);
}
