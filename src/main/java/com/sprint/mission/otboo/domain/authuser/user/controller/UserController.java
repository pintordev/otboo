package com.sprint.mission.otboo.domain.authuser.user.controller;

import com.sprint.mission.otboo.domain.authuser.user.controller.api.UserApi;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController implements UserApi {

  private final UserService userService;

  @Override
  @PostMapping("")
  public ResponseEntity<UserDto> signUp(@Valid @RequestBody UserCreateRequest request) {
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(userService.signUp(request));
  }
}
