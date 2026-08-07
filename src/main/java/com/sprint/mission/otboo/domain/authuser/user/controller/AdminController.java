package com.sprint.mission.otboo.domain.authuser.user.controller;

import com.sprint.mission.otboo.domain.authuser.user.controller.api.AdminApi;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.service.AdminService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController implements AdminApi {

  private final AdminService adminService;

  @Override
  @GetMapping("")
  public ResponseEntity<CursorPageResponse<UserDto>> searchUserList(
      @Valid @ModelAttribute UserListParams condition
  ) {
    CursorPageResponse<UserDto> result = adminService.searchUserList(condition);
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(result);
  }

  @Override
  @PatchMapping("/{userId}/role")
  public ResponseEntity<UserDto> changeRole(
      @PathVariable UUID userId,
      @Valid @RequestBody UserRoleUpdateRequest request
  ) {
    UserDto result = adminService.changeRole(userId, request);
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(result);
  }

  @Override
  @PatchMapping("/{userId}/lock")
  public ResponseEntity<UserDto> changeLock(
      @PathVariable UUID userId,
      @Valid @RequestBody UserLockUpdateRequest request
  ) {
    UserDto result = adminService.changeLock(userId, request);
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(result);
  }
}
