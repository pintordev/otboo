package com.sprint.mission.otboo.domain.authuser.auth.service;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.RefreshDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.event.TempPasswordRequestedEvent;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.InvalidCredentialsException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.event.UserSignedInEvent;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.TokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserSessionRegistry userSessionRegistry;
  private final TokenProvider tokenProvider;
  private final AuthenticationManager authenticationManager;
  private final AuthMapper authMapper;
  private final UserRepository userRepository;
  private final TempPasswordRegistry tempPasswordRegistry;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void signOut(String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      return; // 쿠키 자체가 없으면 이미 로그아웃된 상태로 봄
    }

    RefreshTokenClaims claims;
    try {
      claims = tokenProvider.parseRefreshToken(refreshToken);
    } catch (TokenException e) {
      return; // 만료/위조/형식오류 토큰도 결과적으로 "로그아웃된 상태"와 같으므로 성공 처리
    }

    userSessionRegistry.revoke(claims.userId(), claims.sessionId());
  }

  public SignInDto signIn(SignInRequest request) {
    Authentication authentication = authenticate(request);

    CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
    UserDto userDto = principal.getUserDto();

    Instant now = Instant.now(clock);
    UserSession issued = userSessionRegistry.issue(
        userDto.id(),
        now
    );

    String accessToken = tokenProvider.createAccessToken(
        userDto.id(),
        issued.sessionId(),
        userDto.role().name(),
        now
    );

    String refreshToken = tokenProvider.createRefreshToken(
        userDto.id(),
        issued.sessionId(),
        issued.currentRefreshJti(),
        now
    );

    // 같은 계정으로 이미 로그인된 세션이 있으면 기존 SSE 연결을 강제
    // TODO: 단일 기기 로그인이라는 정책에 묶여있음 (추후 수정 필요)
    eventPublisher.publishEvent(new UserSignedInEvent(principal.getUserDto().id()));

    return authMapper.signInDtoFrom(userDto, accessToken, refreshToken);
  }

  private Authentication authenticate(SignInRequest request) {
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()
          )
      );
    } catch (LockedException e) {
      throw AccountLockedException.withNone();
    } catch (AuthenticationException e) {
      throw InvalidCredentialsException.withNone();
    }
  }
  
  public void resetPassword(ResetPasswordRequest request) {
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(UserNotFoundException::withNone);

    String rawTempPassword = tempPasswordRegistry.issue(user.getId());
    eventPublisher.publishEvent(
        new TempPasswordRequestedEvent(user.getEmail(), rawTempPassword,
            tempPasswordRegistry.getExpirationMinutes()));
  }

  public RefreshDto refresh(String refreshToken) {

    RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken);

    User foundUser = userRepository.findById(claims.userId())
        .orElseThrow(UserNotFoundException::withNone);

    if (foundUser.isLocked()) {
      userSessionRegistry.revokeAll(foundUser.getId());
      throw AccountLockedException.withNone();
    }

    Instant now = Instant.now(clock);
    UserSession rotated = userSessionRegistry.rotate(
        foundUser.getId(),
        claims.sessionId(),
        claims.jti(),
        now
    );

    String newAccessToken = tokenProvider.createAccessToken(
        foundUser.getId(),
        rotated.sessionId(),
        foundUser.getRole().name(),
        now
    );

    String newRefreshToken = tokenProvider.createRefreshToken(
        foundUser.getId(),
        rotated.sessionId(),
        rotated.currentRefreshJti(),
        now
    );

    return authMapper.refreshDtoFrom(foundUser, newAccessToken, newRefreshToken);
  }
}
