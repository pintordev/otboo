package com.sprint.mission.otboo.domain.authuser.user.controller.api;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "프로필 관리", description = "프로필 관련 API")
public interface UserApi {

  @Operation(summary = "사용자 등록(회원가입)", description = "사용자 등록(회원가입) API")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "사용자 등록(회원가입) 성공"),
      @ApiResponse(responseCode = "400", description = "사용자 등록(회원가입) 실패"),
      @ApiResponse(responseCode = "409", description = "사용자 등록(회원가입) 이메일 중복")
  })
  ResponseEntity<UserDto> signUp(UserCreateRequest request);
}
