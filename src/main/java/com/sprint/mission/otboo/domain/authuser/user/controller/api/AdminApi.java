package com.sprint.mission.otboo.domain.authuser.user.controller.api;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "사용자 관리(관리자)", description = "관리자 전용 사용자 관리 API")
public interface AdminApi {

  @Operation(summary = "사용자 목록 조회", description = "이메일/역할/잠금 여부로 필터링하고 커서 기반으로 페이지네이션된 사용자 목록을 조회합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "사용자 목록 조회 성공"),
      @ApiResponse(responseCode = "400", description = "조회 조건 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
  })
  ResponseEntity<CursorPageResponse<UserDto>> searchUserList(@ParameterObject UserListParams condition);

  @Operation(summary = "사용자 역할 변경", description = "특정 사용자의 역할(권한)을 변경합니다. 변경 즉시 해당 사용자의 모든 세션이 회수되어 재로그인이 필요합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "역할 변경 성공"),
      @ApiResponse(responseCode = "400", description = "요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  ResponseEntity<UserDto> changeRole(UUID userId, UserRoleUpdateRequest request);

  @Operation(summary = "사용자 잠금 상태 변경", description = "특정 사용자를 잠금/잠금 해제합니다. 잠금 처리 시 해당 사용자의 모든 세션이 즉시 회수됩니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "잠금 상태 변경 성공"),
      @ApiResponse(responseCode = "400", description = "요청 값 유효성 검증 실패"),
      @ApiResponse(responseCode = "401", description = "인증되지 않음"),
      @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
      @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
  })
  ResponseEntity<UserDto> changeLock(UUID userId, UserLockUpdateRequest request);
}
