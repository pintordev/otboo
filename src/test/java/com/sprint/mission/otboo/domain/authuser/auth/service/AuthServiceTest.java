package com.sprint.mission.otboo.domain.authuser.auth.service;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.ResetPasswordRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.request.SignInRequest;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.event.TempPasswordRequestedEvent;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.InvalidCredentialsException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import com.sprint.mission.otboo.global.security.jwt.JwtProperties;
import com.sprint.mission.otboo.global.security.jwt.JwtProvider;
import com.sprint.mission.otboo.global.security.jwt.exception.InvalidRefreshTokenException;
import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.global.usersession.UserSession;
import com.sprint.mission.otboo.global.usersession.UserSessionRegistry;
import com.sprint.mission.otboo.global.usersession.exception.RefreshTokenReusedException;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionExpiredException;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String ACCESS_SECRET =
      "dGVzdC1hY2Nlc3Mtc2VjcmV0LWtleS1mb3Itand0LXByb3ZpZGVyLXVuaXQtdGVzdC1wbGVhc2U=";
  private static final String REFRESH_SECRET =
      "dGVzdC1yZWZyZXNoLXNlY3JldC1rZXktZm9yLWp3dC1wcm92aWRlci11bml0LXRlc3QtcGxlYXNl";
  private static final JwtProvider realJwtProviderForFixtures =
      new JwtProvider(new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, 15L, 14L));

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  private static final FixtureMonkey entityFixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  AuthService authService;
  @Mock
  UserSessionRegistry mockUserSessionRegistry;
  @Mock
  JwtProvider mockJwtProvider;
  @Mock
  AuthenticationManager mockAuthenticationManager;
  @Mock
  UserRepository mockUserRepository;
  @Mock
  SseService mockSseService;
  @Spy
  AuthMapper authMapper = new AuthMapper(new UserMapper());
  @Mock
  TempPasswordGenerator mockTempPasswordGenerator;
  @Mock
  TempPasswordRegistry mockTempPasswordRegistry;
  @Mock
  ApplicationEventPublisher mockEventPublisher;

  private User fixtureUnlockedUser() {
    User user = entityFixtureMonkey.giveMeBuilder(User.class)
        .set("id", UUID.randomUUID())
        .set("role", Role.USER)
        .sample();
    user.unlock();
    return user;
  }

  private Claims refreshClaims(UUID userId, UUID sid, UUID jti) {
    String token = realJwtProviderForFixtures.createRefreshToken(userId, sid, jti, Instant.now());
    return realJwtProviderForFixtures.parseRefreshTokenClaims(token);
  }

  @Nested
  @DisplayName("로그인 성공")
  class SignInSuccess {

    @Test
    @DisplayName("AuthenticationManager 인증에 성공하면 세션을 발급하고 토큰을 생성해 SignInDto를 반환한다")
    void signIn_success_returnsSignInDto() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      User user = User.create("홍길동", request.username(), "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      Authentication authentication = mock(Authentication.class);
      given(authentication.getPrincipal()).willReturn(principal);
      given(mockAuthenticationManager.authenticate(any())).willReturn(authentication);

      UserSession issuedSession = new UserSession(UUID.randomUUID(), UUID.randomUUID(),
          Instant.now());
      Instant refreshExpiresAt = Instant.now().plus(14, ChronoUnit.DAYS);

      given(mockUserSessionRegistry.issue()).willReturn(issuedSession);
      given(mockJwtProvider.createAccessToken(
          principal.getUserId(), principal.getRole().name(), issuedSession.sessionId(),
          issuedSession.issuedAt()))
          .willReturn("access-token");
      given(mockJwtProvider.createRefreshToken(
          principal.getUserId(), issuedSession.sessionId(), issuedSession.currentRefreshJti(),
          issuedSession.issuedAt()))
          .willReturn("refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(issuedSession.issuedAt())).willReturn(
          refreshExpiresAt);

      // when
      SignInDto result = authService.signIn(request);

      // then
      assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
      assertThat(result.refreshToken()).isEqualTo("refresh-token");
      assertThat(result.jwtDto().userDto().email()).isEqualTo(user.getEmail());
      verify(mockUserSessionRegistry).save(principal.getUserId(), issuedSession, refreshExpiresAt);
    }

    @Test
    @DisplayName("AuthenticationManager에는 폼에 입력된 username/password 그대로 인증되지 않은 토큰을 전달한다")
    void signIn_success_authenticatesWithRawCredentials() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      User user = User.create("홍길동", request.username(), "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      Authentication authentication = mock(Authentication.class);
      given(authentication.getPrincipal()).willReturn(principal);
      given(mockAuthenticationManager.authenticate(any())).willReturn(authentication);
      given(mockUserSessionRegistry.issue())
          .willReturn(new UserSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "access-token");
      given(mockJwtProvider.createRefreshToken(any(), any(), any(), any())).willReturn(
          "refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.signIn(request);

      // then
      ArgumentCaptor<Authentication> tokenCaptor = ArgumentCaptor.forClass(Authentication.class);
      verify(mockAuthenticationManager).authenticate(tokenCaptor.capture());
      UsernamePasswordAuthenticationToken captured = (UsernamePasswordAuthenticationToken) tokenCaptor.getValue();
      assertThat(captured.getPrincipal()).isEqualTo(request.username());
      assertThat(captured.getCredentials()).isEqualTo(request.password());
      assertThat(captured.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("세션 저장(save)은 토큰 생성이 모두 끝난 뒤 가장 마지막에 호출된다 (트랜잭션 롤백 영향 최소화)")
    void signIn_success_savesSessionLast() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      User user = User.create("홍길동", request.username(), "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      Authentication authentication = mock(Authentication.class);
      given(authentication.getPrincipal()).willReturn(principal);
      given(mockAuthenticationManager.authenticate(any())).willReturn(authentication);

      UserSession issuedSession = new UserSession(UUID.randomUUID(), UUID.randomUUID(),
          Instant.now());
      given(mockUserSessionRegistry.issue()).willReturn(issuedSession);
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "access-token");
      given(mockJwtProvider.createRefreshToken(any(), any(), any(), any())).willReturn(
          "refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.signIn(request);

      // then
      InOrder inOrder = inOrder(mockUserSessionRegistry, mockJwtProvider);
      inOrder.verify(mockUserSessionRegistry).issue();
      inOrder.verify(mockJwtProvider).createAccessToken(any(), any(), any(), any());
      inOrder.verify(mockJwtProvider).createRefreshToken(any(), any(), any(), any());
      inOrder.verify(mockUserSessionRegistry).save(any(), any(), any());
    }

    @Test
    @DisplayName("로그인_성공_시_같은_계정의_기존_SSE_연결을_강제_종료한다")
    void 로그인_성공_시_같은_계정의_기존_SSE_연결을_강제_종료한다() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      User user = User.create("홍길동", request.username(), "encoded-password");
      CustomUserDetails principal = new CustomUserDetails(user);

      Authentication authentication = mock(Authentication.class);
      given(authentication.getPrincipal()).willReturn(principal);
      given(mockAuthenticationManager.authenticate(any())).willReturn(authentication);

      UserSession issuedSession = new UserSession(UUID.randomUUID(), UUID.randomUUID(),
          Instant.now());
      given(mockUserSessionRegistry.issue()).willReturn(issuedSession);
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "access-token");
      given(mockJwtProvider.createRefreshToken(any(), any(), any(), any())).willReturn(
          "refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.signIn(request);

      // then
      verify(mockSseService).disconnectAll(principal.getUserId());
    }
  }

  @Nested
  @DisplayName("자격증명 실패")
  class InvalidCredentials {

    @Test
    @DisplayName("AuthenticationManager가 BadCredentialsException을 던지면 InvalidCredentialsException으로 변환하고 세션을 발급하지 않는다")
    void signIn_badCredentials_throwsInvalidCredentialsExceptionAndDoesNotIssueSession() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      given(mockAuthenticationManager.authenticate(any()))
          .willThrow(new BadCredentialsException("Bad credentials"));

      // when & then
      assertThatThrownBy(() -> authService.signIn(request))
          .isInstanceOf(InvalidCredentialsException.class);

      verify(mockUserSessionRegistry, never()).issue();
      verify(mockJwtProvider, never()).createAccessToken(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("계정 잠김")
  class AccountLocked {

    @Test
    @DisplayName("AuthenticationManager가 LockedException을 던지면 AccountLockedException으로 변환하고 세션을 발급하지 않는다")
    void signIn_locked_throwsAccountLockedExceptionAndDoesNotIssueSession() {
      // given
      SignInRequest request = fixtureMonkey.giveMeBuilder(SignInRequest.class).sample();
      given(mockAuthenticationManager.authenticate(any()))
          .willThrow(new LockedException("Account is locked"));

      // when & then
      assertThatThrownBy(() -> authService.signIn(request))
          .isInstanceOf(AccountLockedException.class);

      verify(mockUserSessionRegistry, never()).issue();
    }
  }

  @Nested
  @DisplayName("로그아웃")
  class SignOut {

    @Test
    @DisplayName("주어진 userId로 세션을 폐기(revoke)한다")
    void signOut_revokesSessionForGivenUserId() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      authService.signOut(userId);

      // then
      verify(mockUserSessionRegistry).revoke(userId);
    }
  }

  @Nested
  @DisplayName("토큰 재발급 성공")
  class RefreshSuccess {

    @Test
    @DisplayName("유효한 refresh token이면 세션을 회전하고 새 토큰을 발급해 SignInDto를 반환한다")
    void refresh_success_returnsSignInDto() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims("refresh-token"))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));

      UUID newJti = UUID.randomUUID();
      Instant originalIssuedAt = Instant.now().minus(3, ChronoUnit.DAYS);
      UserSession rotated = new UserSession(sid, newJti, originalIssuedAt);
      given(mockUserSessionRegistry.rotate(user.getId(), sid, jti)).willReturn(rotated);

      given(mockJwtProvider.createAccessToken(eq(user.getId()), eq(user.getRole().name()), eq(sid),
          any(Instant.class)))
          .willReturn("new-access-token");
      given(mockJwtProvider.createRefreshToken(user.getId(), sid, newJti, originalIssuedAt))
          .willReturn("new-refresh-token");
      Instant refreshExpiresAt = originalIssuedAt.plus(14, ChronoUnit.DAYS);
      given(mockJwtProvider.getRefreshTokenExpiresAt(originalIssuedAt)).willReturn(
          refreshExpiresAt);

      // when
      SignInDto result = authService.refresh("refresh-token");

      // then
      assertThat(result.jwtDto().accessToken()).isEqualTo("new-access-token");
      assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
      assertThat(result.jwtDto().userDto().email()).isEqualTo(user.getEmail());
      verify(mockUserSessionRegistry).save(user.getId(), rotated, refreshExpiresAt);
    }

    @Test
    @DisplayName("access token은 절대 만료 기준(rotated.issuedAt())이 아니라 현재 시각을 기준으로 새로 발급된다")
    void refresh_success_createsAccessTokenWithCurrentInstant() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));

      // 절대 만료 정책: 원래 로그인이 오래 전이라 issuedAt이 과거
      Instant staleIssuedAt = Instant.now().minus(10, ChronoUnit.DAYS);
      UserSession rotated = new UserSession(sid, UUID.randomUUID(), staleIssuedAt);
      given(mockUserSessionRegistry.rotate(any(), any(), any())).willReturn(rotated);
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "new-access-token");
      given(mockJwtProvider.createRefreshToken(any(), any(), any(), any())).willReturn(
          "new-refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.refresh("refresh-token");

      // then
      ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(mockJwtProvider).createAccessToken(any(), any(), any(), nowCaptor.capture());
      assertThat(nowCaptor.getValue()).isNotEqualTo(staleIssuedAt);
      assertThat(nowCaptor.getValue()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("refresh token은 절대 만료 기준(rotated.issuedAt())을 그대로 사용해 발급된다")
    void refresh_success_createsRefreshTokenWithOriginalIssuedAt() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));

      Instant originalIssuedAt = Instant.now().minus(5, ChronoUnit.DAYS);
      UUID newJti = UUID.randomUUID();
      UserSession rotated = new UserSession(sid, newJti, originalIssuedAt);
      given(mockUserSessionRegistry.rotate(any(), any(), any())).willReturn(rotated);
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "new-access-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.refresh("refresh-token");

      // then
      verify(mockJwtProvider).createRefreshToken(user.getId(), sid, newJti, originalIssuedAt);
    }

    @Test
    @DisplayName("세션 저장(save)은 토큰 생성이 모두 끝난 뒤 가장 마지막에 호출된다")
    void refresh_success_savesSessionLast() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));

      UserSession rotated = new UserSession(sid, UUID.randomUUID(), Instant.now());
      given(mockUserSessionRegistry.rotate(any(), any(), any())).willReturn(rotated);
      given(mockJwtProvider.createAccessToken(any(), any(), any(), any())).willReturn(
          "new-access-token");
      given(mockJwtProvider.createRefreshToken(any(), any(), any(), any())).willReturn(
          "new-refresh-token");
      given(mockJwtProvider.getRefreshTokenExpiresAt(any())).willReturn(Instant.now());

      // when
      authService.refresh("refresh-token");

      // then
      InOrder inOrder = inOrder(mockUserSessionRegistry, mockJwtProvider);
      inOrder.verify(mockUserSessionRegistry).rotate(any(), any(), any());
      inOrder.verify(mockJwtProvider).createAccessToken(any(), any(), any(), any());
      inOrder.verify(mockJwtProvider).createRefreshToken(any(), any(), any(), any());
      inOrder.verify(mockUserSessionRegistry).save(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("토큰 재발급 - 사용자를 찾을 수 없음")
  class RefreshUserNotFound {

    @Test
    @DisplayName("토큰의 사용자 ID로 사용자를 찾을 수 없으면 UserNotFoundException을 던지고 세션 회전을 시도하지 않는다")
    void refresh_userNotFound_throwsUserNotFoundExceptionAndDoesNotRotate() {
      // given
      UUID userId = UUID.randomUUID();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(userId, sid, jti));
      given(mockUserRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> authService.refresh("refresh-token"))
          .isInstanceOf(UserNotFoundException.class);

      verify(mockUserSessionRegistry, never()).rotate(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("토큰 재발급 - 계정 잠김")
  class RefreshAccountLocked {

    @Test
    @DisplayName("계정이 잠겨 있으면 세션을 폐기하고 AccountLockedException을 던지며, 세션 회전은 시도하지 않는다")
    void refresh_lockedAccount_revokesSessionAndThrowsAccountLockedException() {
      // given
      User lockedUser = entityFixtureMonkey.giveMeBuilder(User.class)
          .set("id", UUID.randomUUID())
          .set("email", "hong@test.com")
          .sample();
      lockedUser.lock(LockReason.ADMIN_ACTION);

      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(lockedUser.getId(), sid, jti));
      given(mockUserRepository.findById(lockedUser.getId())).willReturn(Optional.of(lockedUser));

      // when & then
      assertThatThrownBy(() -> authService.refresh("refresh-token"))
          .isInstanceOf(AccountLockedException.class);

      verify(mockUserSessionRegistry).revoke(lockedUser.getId());
      verify(mockUserSessionRegistry, never()).rotate(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("토큰 재발급 - 리프레시 토큰 자체가 유효하지 않음")
  class InvalidRefreshToken {

    @Test
    @DisplayName("JwtProvider가 파싱 단계에서 예외를 던지면 그대로 전파되고 사용자 조회조차 하지 않는다")
    void refresh_invalidToken_propagatesExceptionAndDoesNotLookUpUser() {
      // given
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willThrow(InvalidRefreshTokenException.withNone());

      // when & then
      assertThatThrownBy(() -> authService.refresh("bad-token"))
          .isInstanceOf(InvalidRefreshTokenException.class);

      verify(mockUserRepository, never()).findById(any());
    }
  }

  @Nested
  @DisplayName("토큰 재발급 - 세션 회전 실패")
  class RotateFailure {

    @Test
    @DisplayName("세션이 이미 만료/무효화됐으면 UserSessionExpiredException이 그대로 전파되고 토큰을 새로 발급하지 않는다")
    void refresh_sessionExpired_propagatesExceptionAndDoesNotIssueNewTokens() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));
      given(mockUserSessionRegistry.rotate(user.getId(), sid, jti))
          .willThrow(UserSessionExpiredException.withNone());

      // when & then
      assertThatThrownBy(() -> authService.refresh("refresh-token"))
          .isInstanceOf(UserSessionExpiredException.class);

      verify(mockJwtProvider, never()).createAccessToken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 사용된(탈취 의심) refresh token이면 RefreshTokenReusedException이 그대로 전파된다")
    void refresh_reusedToken_propagatesRefreshTokenReusedException() {
      // given
      User user = fixtureUnlockedUser();
      UUID sid = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      given(mockJwtProvider.parseRefreshTokenClaims(anyString()))
          .willReturn(refreshClaims(user.getId(), sid, jti));
      given(mockUserRepository.findById(user.getId())).willReturn(Optional.of(user));
      given(mockUserSessionRegistry.rotate(user.getId(), sid, jti))
          .willThrow(RefreshTokenReusedException.withNone());

      // when & then
      assertThatThrownBy(() -> authService.refresh("refresh-token"))
          .isInstanceOf(RefreshTokenReusedException.class);

      verify(mockJwtProvider, never()).createAccessToken(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("임시 비밀번호 발급")
  class ResetPassword {

    @Test
    @DisplayName("가입된 이메일이면 임시 비밀번호를 생성해 저장하고 발급 이벤트를 발행한다")
    void resetPassword_existingEmail_savesTempPasswordAndPublishesEvent() {
      // given
      User user = fixtureUnlockedUser();
      ResetPasswordRequest request = new ResetPasswordRequest(user.getEmail());
      given(mockUserRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
      given(mockTempPasswordGenerator.generate()).willReturn("Temp123!@#$");

      // when
      authService.resetPassword(request);

      // then
      verify(mockTempPasswordRegistry).save(user.getId(), "Temp123!@#$");

      ArgumentCaptor<TempPasswordRequestedEvent> eventCaptor =
          ArgumentCaptor.forClass(TempPasswordRequestedEvent.class);
      verify(mockEventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().email()).isEqualTo(user.getEmail());
      assertThat(eventCaptor.getValue().rawTempPassword()).isEqualTo("Temp123!@#$");
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 아무 것도 하지 않고 예외 없이 종료한다 (이메일 존재 여부 노출 방지)")
    void resetPassword_unknownEmail_doesNothingAndDoesNotThrow() {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest("unknown@test.com");
      given(mockUserRepository.findByEmail(request.email())).willReturn(Optional.empty());

      // when & then
      assertThatCode(() -> authService.resetPassword(request)).doesNotThrowAnyException();

      verify(mockTempPasswordGenerator, never()).generate();
      verify(mockTempPasswordRegistry, never()).save(any(), any());
      verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("호출할 때마다 새로운 임시 비밀번호를 생성기로부터 새로 발급받는다")
    void resetPassword_calledTwice_generatesTempPasswordEachTime() {
      // given
      User user = fixtureUnlockedUser();
      ResetPasswordRequest request = new ResetPasswordRequest(user.getEmail());
      given(mockUserRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
      given(mockTempPasswordGenerator.generate()).willReturn("first-pw", "second-pw");

      // when
      authService.resetPassword(request);
      authService.resetPassword(request);

      // then
      verify(mockTempPasswordGenerator, times(2)).generate();
      verify(mockTempPasswordRegistry).save(user.getId(), "first-pw");
      verify(mockTempPasswordRegistry).save(user.getId(), "second-pw");
    }
  }
}
