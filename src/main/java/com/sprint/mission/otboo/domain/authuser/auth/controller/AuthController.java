package com.sprint.mission.otboo.domain.authuser.auth.controller;

import com.sprint.mission.otboo.domain.authuser.auth.controller.api.AuthApi;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.RefreshDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.auth.service.AuthService;
import com.sprint.mission.otboo.security.cookie.provider.RefreshTokenCookieProvider;
import com.sprint.mission.otboo.security.details.CurrentUser;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final RefreshTokenCookieProvider refreshTokenCookieProvider;


  @Override
  @PostMapping("/sign-out")
  public ResponseEntity<Void> signOut(
      @CookieValue(name = RefreshTokenCookieProvider.REFRESH_TOKEN, defaultValue = "") String refreshToken,
      HttpServletResponse response
  ) {
    authService.signOut(refreshToken);
    refreshTokenCookieProvider.clear(response);
    return ResponseEntity
        .status(HttpStatus.OK)
        .build();
  }

  @Override
  @PostMapping("/sign-in")
  public ResponseEntity<JwtDto> signIn(
      @Valid @ModelAttribute SignInRequest request,
      HttpServletResponse response
  ) {
    SignInDto result = authService.signIn(request);
    refreshTokenCookieProvider.attach(response, result.refreshToken());
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(result.jwtDto());
  }

  @Override
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity
        .status(HttpStatus.OK)
        .build();
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<JwtDto> refresh(
      @CookieValue(name = RefreshTokenCookieProvider.REFRESH_TOKEN, defaultValue = "") String refreshToken,
      HttpServletResponse response
  ) {
    RefreshDto result = authService.refresh(refreshToken);
    refreshTokenCookieProvider.attach(response, result.refreshToken());
    return ResponseEntity
        .status(HttpStatus.OK)
        .body(result.jwtDto());
  }

  @Override
  @GetMapping("/csrf-token")
  public ResponseEntity<Void> csrfToken(CsrfToken csrfToken) {
    csrfToken.getToken();
    return ResponseEntity
        .status(HttpStatus.NO_CONTENT)
        .build();
  }
}
