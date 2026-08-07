package com.sprint.mission.otboo.domain.authuser.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.RefreshDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.event.TempPasswordRequestedEvent;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.InvalidCredentialsException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import com.sprint.mission.otboo.security.token.dto.RefreshTokenClaims;
import com.sprint.mission.otboo.security.token.exception.business.InvalidRefreshTokenException;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @InjectMocks
  private AuthService authService;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private AuthMapper authMapper;

  @Mock
  private UserRepository userRepository;

  @Mock
  private SseService sseService;

  @Mock
  private TempPasswordGenerator tempPasswordGenerator;

  @Mock
  private TempPasswordRegistry tempPasswordRegistry;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private Clock clock;

  private UserDto userDtoOf(UUID userId, Role role) {
    return new UserDto(userId, NOW, "hong@test.com", "홍길동", role, false);
  }

  private Authentication authenticationOf(UserDto userDto) {
    CustomUserDetails principal = new CustomUserDetails(userDto, null);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(userDto.role().name())));
  }

  @Nested
  @DisplayName("로그아웃 (signOut)")
  class SignOut {

    @Test
    @DisplayName("refreshToken이 비어있으면 아무 것도 하지 않는다")
    void refreshToken이_비어있으면_아무_것도_하지_않는다() {
      // given
      String blankRefreshToken = "";

      // when
      authService.signOut(blankRefreshToken);

      // then
      verify(userSessionRegistry, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("refreshToken이 유효하지 않으면 조용히 종료한다")
    void refreshToken이_유효하지_않으면_조용히_종료한다() {
      // given
      given(tokenProvider.parseRefreshToken("invalid-token"))
          .willThrow(InvalidRefreshTokenException.withNone());

      // when
      authService.signOut("invalid-token");

      // then
      verify(userSessionRegistry, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("유효한 refreshToken이면 해당 세션을 회수한다")
    void 유효한_refreshToken이면_해당_세션을_회수한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      RefreshTokenClaims claims = new RefreshTokenClaims(userId, sessionId, UUID.randomUUID());
      given(tokenProvider.parseRefreshToken("valid-token")).willReturn(claims);

      // when
      authService.signOut("valid-token");

      // then
      verify(userSessionRegistry).revoke(userId, sessionId);
    }
  }

  @Nested
  @DisplayName("로그인 (signIn)")
  class SignIn {

    @Test
    @DisplayName("인증에 성공하면 세션을 발급하고 토큰을 생성해 SignInDto를 반환한다")
    void 인증에_성공하면_세션을_발급하고_토큰을_생성해_SignInDto를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      UserDto userDto = userDtoOf(userId, Role.USER);
      SignInRequest request = new SignInRequest("hong@test.com", "password1");
      given(authenticationManager.authenticate(any())).willReturn(authenticationOf(userDto));
      given(clock.instant()).willReturn(NOW);

      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(userId, sessionId, jti, NOW);
      given(userSessionRegistry.issue(userId, NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(userId, sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(userId, sessionId, jti, NOW))
          .willReturn("refresh-token");

      SignInDto expected =
          new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token");
      given(authMapper.signInDtoFrom(userDto, "access-token", "refresh-token")).willReturn(expected);

      // when
      SignInDto result = authService.signIn(request);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("로그인에 성공하면 기존 SSE 연결을 강제 종료한다")
    void 로그인에_성공하면_기존_SSE_연결을_강제_종료한다() {
      // given
      UUID userId = UUID.randomUUID();
      UserDto userDto = userDtoOf(userId, Role.USER);
      SignInRequest request = new SignInRequest("hong@test.com", "password1");
      given(authenticationManager.authenticate(any())).willReturn(authenticationOf(userDto));
      given(clock.instant()).willReturn(NOW);

      UserSession issued = new UserSession(userId, UUID.randomUUID(), UUID.randomUUID(), NOW);
      given(userSessionRegistry.issue(eq(userId), any())).willReturn(issued);

      // when
      authService.signIn(request);

      // then
      verify(sseService).disconnectAll(userId);
    }

    @Test
    @DisplayName("계정이 잠겨있으면 AccountLockedException을 던진다")
    void 계정이_잠겨있으면_AccountLockedException을_던진다() {
      // given
      SignInRequest request = new SignInRequest("hong@test.com", "password1");
      willThrow(new LockedException("계정 잠김"))
          .given(authenticationManager).authenticate(any());

      // when & then
      assertThatThrownBy(() -> authService.signIn(request))
          .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 틀리면 InvalidCredentialsException을 던진다")
    void 아이디_또는_비밀번호가_틀리면_InvalidCredentialsException을_던진다() {
      // given
      SignInRequest request = new SignInRequest("hong@test.com", "wrong-password");
      willThrow(new BadCredentialsException("자격 증명 실패"))
          .given(authenticationManager).authenticate(any());

      // when & then
      assertThatThrownBy(() -> authService.signIn(request))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }

  @Nested
  @DisplayName("임시 비밀번호 발급 (resetPassword)")
  class ResetPassword {

    @Test
    @DisplayName("가입된 이메일이면 임시 비밀번호를 생성해 저장하고 이벤트를 발행한다")
    void 가입된_이메일이면_임시_비밀번호를_생성해_저장하고_이벤트를_발행한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      given(userRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(tempPasswordGenerator.generate()).willReturn("temp-password!");

      ResetPasswordRequest request = new ResetPasswordRequest("hong@test.com");

      // when
      authService.resetPassword(request);

      // then
      verify(tempPasswordRegistry).save(user.getId(), "temp-password!");
      verify(eventPublisher).publishEvent(
          new TempPasswordRequestedEvent("hong@test.com", "temp-password!"));
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 아무 것도 하지 않는다")
    void 가입되지_않은_이메일이면_아무_것도_하지_않는다() {
      // given
      given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());
      ResetPasswordRequest request = new ResetPasswordRequest("unknown@test.com");

      // when
      authService.resetPassword(request);

      // then
      verify(tempPasswordRegistry, never()).save(any(), any());
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("액세스 토큰 재발급 (refresh)")
  class Refresh {

    @Test
    @DisplayName("유효한 refreshToken이면 세션을 회전하고 새 토큰들을 반환한다")
    void 유효한_refreshToken이면_세션을_회전하고_새_토큰들을_반환한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      RefreshTokenClaims claims = new RefreshTokenClaims(user.getId(), sessionId, jti);
      given(tokenProvider.parseRefreshToken("valid-refresh-token")).willReturn(claims);
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
      given(clock.instant()).willReturn(NOW);

      UUID newJti = UUID.randomUUID();
      UserSession rotated = new UserSession(user.getId(), sessionId, newJti, NOW);
      given(userSessionRegistry.rotate(user.getId(), sessionId, jti, NOW)).willReturn(rotated);
      given(tokenProvider.createAccessToken(user.getId(), sessionId, "USER", NOW))
          .willReturn("new-access-token");
      given(tokenProvider.createRefreshToken(user.getId(), sessionId, newJti, NOW))
          .willReturn("new-refresh-token");

      RefreshDto expected = new RefreshDto(
          new JwtDto(userDtoOf(user.getId(), Role.USER), "new-access-token"), "new-refresh-token");
      given(authMapper.refreshDtoFrom(user, "new-access-token", "new-refresh-token"))
          .willReturn(expected);

      // when
      RefreshDto result = authService.refresh("valid-refresh-token");

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("토큰의 사용자를 찾을 수 없으면 UserNotFoundException을 던진다")
    void 토큰의_사용자를_찾을_수_없으면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      RefreshTokenClaims claims = new RefreshTokenClaims(userId, UUID.randomUUID(), UUID.randomUUID());
      given(tokenProvider.parseRefreshToken("valid-refresh-token")).willReturn(claims);
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> authService.refresh("valid-refresh-token"))
          .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("계정이 잠겨있으면 모든 세션을 회수하고 AccountLockedException을 던진다")
    void 계정이_잠겨있으면_모든_세션을_회수하고_AccountLockedException을_던진다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      user.lock(LockReason.ADMIN_ACTION);
      RefreshTokenClaims claims =
          new RefreshTokenClaims(user.getId(), UUID.randomUUID(), UUID.randomUUID());
      given(tokenProvider.parseRefreshToken("valid-refresh-token")).willReturn(claims);
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

      // when & then
      assertThatThrownBy(() -> authService.refresh("valid-refresh-token"))
          .isInstanceOf(AccountLockedException.class);
      verify(userSessionRegistry).revokeAll(user.getId());
      verify(userSessionRegistry, never()).rotate(any(), any(), any(), any());
    }
  }
}
