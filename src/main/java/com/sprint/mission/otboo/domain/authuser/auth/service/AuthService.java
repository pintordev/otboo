package com.sprint.mission.otboo.domain.authuser.auth.service;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.event.TempPasswordRequestedEvent;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.InvalidCredentialsException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserSessionRegistry userSessionRegistry;
  private final TokenProvider tokenProvider;
  private final AuthenticationManager authenticationManager;
  private final AuthMapper authMapper;
  private final UserRepository userRepository;
  private final SseService sseService;
  private final TempPasswordGenerator tempPasswordGenerator;
  private final TempPasswordRegistry tempPasswordRegistry;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void signOut(UUID userId) {
    userSessionRegistry.revoke(userId);
  }

  public SignInDto signIn(SignInRequest request) {

    Authentication authentication = authenticate(request);
    CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

    Instant now = Instant.now(clock);
    UserSession issued = userSessionRegistry.issue(
        principal.getUserId(), now, tokenProvider.getRefreshTokenTtl());

    String accessToken = tokenProvider.createAccessToken(
        principal.getUserId(), principal.getRole().name(), issued.sessionId(), issued.issuedAt());

    String refreshToken = tokenProvider.createRefreshToken(
        principal.getUserId(), issued.currentRefreshJti(), issued.sessionId(),
        issued.issuedAt(), tokenProvider.getRefreshTokenExpiresAt(issued.issuedAt()));

    SignInDto result = authMapper.signInDtoFrom(principal, accessToken, refreshToken);

    // 같은 계정으로 이미 로그인된 세션이 있으면 기존 SSE 연결을 강제 종료
    sseService.disconnectAll(principal.getUserId());

    return result;
  }

  private Authentication authenticate(SignInRequest request) {
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()
          )
      );
    } catch (LockedException e) {
      throw AccountLockedException.withEmail(request.username());
    } catch (AuthenticationException e) {
      throw InvalidCredentialsException.withCause(e);
    }
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    userRepository.findByEmail(request.email())
        .ifPresent(user -> {
          String rawTempPassword = tempPasswordGenerator.generate();
          tempPasswordRegistry.save(user.getId(), rawTempPassword);
          eventPublisher.publishEvent(
              new TempPasswordRequestedEvent(user.getEmail(), rawTempPassword));
        });
  }

  public SignInDto refresh(String refreshToken) {

    RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken);

    User foundUser = userRepository.findById(claims.userId())
        .orElseThrow(UserNotFoundException::withNone);

    if (foundUser.isLocked()) {
      userSessionRegistry.revoke(foundUser.getId());
      throw AccountLockedException.withEmail(foundUser.getEmail());
    }

    UserSession rotated = userSessionRegistry.rotate(
        foundUser.getId(), claims.sessionId(), claims.jti(), tokenProvider.getRefreshTokenTtl());

    String newAccessToken = tokenProvider.createAccessToken(
        foundUser.getId(), foundUser.getRole().name(), rotated.sessionId(), Instant.now(clock));
    String newRefreshToken = tokenProvider.createRefreshToken(
        foundUser.getId(), rotated.currentRefreshJti(), rotated.sessionId(),
        rotated.issuedAt(), tokenProvider.getRefreshTokenExpiresAt(rotated.issuedAt()));

    CustomUserDetails principal = new CustomUserDetails(foundUser);
    return authMapper.signInDtoFrom(principal, newAccessToken, newRefreshToken);
  }
}
