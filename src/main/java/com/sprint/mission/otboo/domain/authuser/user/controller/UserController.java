package com.sprint.mission.otboo.domain.authuser.user.controller;

import com.sprint.mission.otboo.domain.authuser.user.controller.api.UserApi;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ChangePasswordRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ProfileUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.service.UserService;
import com.sprint.mission.otboo.security.details.CurrentUser;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController implements UserApi {

  private final UserService userService;

  @Override
  @PostMapping("")
  public ResponseEntity<UserDto> signUp(@Valid @RequestBody UserCreateRequest request) {
    UserDto result = userService.signUp(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(result);
  }

  @Override
  @GetMapping("/{userId}/profiles")
  public ResponseEntity<ProfileDto> getProfile(
      @PathVariable UUID userId) {
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(userService.getProfile(userId));
  }

  @Override
  @PatchMapping("/{userId}/profiles")
  public ResponseEntity<ProfileDto> changeProfile(
      @PathVariable UUID userId,
      @Valid @RequestPart ProfileUpdateRequest request,
      @RequestPart(required = false) MultipartFile image,
      @CurrentUser UserPrincipal principal) {
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(userService.changeProfile(userId, request, image, principal.userId()));
  }

  @Override
  @PatchMapping("/{userId}/password")
  public ResponseEntity<UserDto> changePassword(
      @PathVariable UUID userId,
      @Valid @RequestBody ChangePasswordRequest request,
      @CurrentUser UserPrincipal principal) {
    userService.changePassword(userId, request, principal.userId());
    return ResponseEntity
        .status(HttpStatus.OK)
        .build();
  }
}
