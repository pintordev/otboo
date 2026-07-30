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
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import com.sprint.mission.otboo.global.security.jwt.JwtProvider;
import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.global.usersession.UserSession;
import com.sprint.mission.otboo.global.usersession.UserSessionRegistry;
import io.jsonwebtoken.Claims;
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
  private final JwtProvider jwtProvider;
  private final AuthenticationManager authenticationManager;
  private final AuthMapper authMapper;
  private final UserRepository userRepository;
  private final SseService sseService;
  private final TempPasswordGenerator tempPasswordGenerator;
  private final TempPasswordRegistry tempPasswordRegistry;
  private final ApplicationEventPublisher eventPublisher;

  public void signOut(UUID userId) {
    userSessionRegistry.revoke(userId);
  }

  public SignInDto signIn(SignInRequest request) {

    Authentication authentication = authenticate(request);
    CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

    // 로그인 성공 이후 세션 / JWT 발급
    UserSession issued = userSessionRegistry.issue();

    String accessToken = jwtProvider.createAccessToken(
        principal.getUserId(),
        principal.getRole().name(),
        issued.sessionId(),
        issued.issuedAt()
    );

    String refreshToken = jwtProvider.createRefreshToken(
        principal.getUserId(),
        issued.sessionId(),
        issued.currentRefreshJti(),
        issued.issuedAt()
    );

    SignInDto result = authMapper.signInDtoFrom(principal, accessToken, refreshToken);

    userSessionRegistry.save(principal.getUserId(), issued,
        jwtProvider.getRefreshTokenExpiresAt(issued.issuedAt()));

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
      throw InvalidCredentialsException.withNone();
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

    Claims claims = jwtProvider.parseRefreshTokenClaims(refreshToken);

    UUID userId = UUID.fromString(claims.getSubject());
    UUID sid = UUID.fromString(claims.get("sid", String.class));
    UUID jti = UUID.fromString(claims.getId());

    User foundUser = userRepository.findById(userId)
        .orElseThrow(() -> UserNotFoundException.withNone());

    if (foundUser.isLocked()) {
      userSessionRegistry.revoke(foundUser.getId());
      throw AccountLockedException.withEmail(foundUser.getEmail());
    }

    UserSession rotated = userSessionRegistry.rotate(userId, sid, jti);

    String newAccessToken = jwtProvider.createAccessToken(
        foundUser.getId(), foundUser.getRole().name(), rotated.sessionId(), Instant.now());
    String newRefreshToken = jwtProvider.createRefreshToken(
        foundUser.getId(), rotated.sessionId(), rotated.currentRefreshJti(), rotated.issuedAt());

    CustomUserDetails principal = new CustomUserDetails(foundUser);
    SignInDto result = authMapper.signInDtoFrom(principal, newAccessToken, newRefreshToken);

    userSessionRegistry.save(foundUser.getId(), rotated,
        jwtProvider.getRefreshTokenExpiresAt(rotated.issuedAt()));

    return result;
  }
}
