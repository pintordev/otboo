package com.sprint.mission.otboo.domain.authuser.auth.authentication;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.details.CustomUserDetails;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TempPasswordAuthenticationProviderTest {

  @InjectMocks
  TempPasswordAuthenticationProvider provider;

  @Mock
  UserRepository mockUserRepository;

  @Mock
  TempPasswordRegistry mockTempPasswordRegistry;

  private User fixtureUser(UUID id, String email) {
    User user = User.create("홍길동", email, "encoded-real-password");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private Authentication unauthenticatedToken(String email, String rawPassword) {
    return UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
  }

  @Nested
  @DisplayName("인증 성공")
  class AuthenticateSuccess {

    @Test
    @DisplayName("가입된 이메일 + 잠기지 않은 계정 + 유효한 임시 비밀번호면 인증된 Authentication을 반환한다")
    void authenticate_validTempPassword_returnsAuthenticatedToken() {
      // given
      UUID userId = UUID.randomUUID();
      User user = fixtureUser(userId, "hong@test.com");
      given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(mockTempPasswordRegistry.matches(userId, "temp-password!")).willReturn(true);

      // when
      Authentication result = provider.authenticate(
          unauthenticatedToken("hong@test.com", "temp-password!"));

      // then
      assertThat(result.isAuthenticated()).isTrue();
      CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
      assertThat(principal.getUserId()).isEqualTo(userId);
      assertThat(principal.getEmail()).isEqualTo("hong@test.com");
    }

    @Test
    @DisplayName("인증에 성공해도 임시 비밀번호를 폐기하지 않는다 (사용자가 명시적으로 비밀번호를 변경할 때만 파기됨)")
    void authenticate_success_doesNotRevokeTempPasswordUntilExplicitPasswordChange() {
      // given
      UUID userId = UUID.randomUUID();
      User user = fixtureUser(userId, "hong@test.com");
      given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(mockTempPasswordRegistry.matches(userId, "temp-password!")).willReturn(true);

      // when
      provider.authenticate(unauthenticatedToken("hong@test.com", "temp-password!"));

      // then
      verify(mockTempPasswordRegistry, never()).revoke(any());
    }
  }

  @Nested
  @DisplayName("인증 실패 - 자격 증명 불일치")
  class AuthenticateFailure {

    @Test
    @DisplayName("가입되지 않은 이메일이면 BadCredentialsException을 던지고 임시 비밀번호는 조회하지 않는다")
    void authenticate_unknownEmail_throwsBadCredentialsException() {
      // given
      given(mockUserRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          provider.authenticate(unauthenticatedToken("unknown@test.com", "temp-password!")))
          .isInstanceOf(BadCredentialsException.class);

      verify(mockTempPasswordRegistry, never()).matches(any(), any());
    }

    @Test
    @DisplayName("임시 비밀번호가 저장된 값과 일치하지 않으면 BadCredentialsException을 던지고 폐기하지 않는다")
    void authenticate_wrongTempPassword_throwsBadCredentialsExceptionAndDoesNotRevoke() {
      // given
      UUID userId = UUID.randomUUID();
      User user = fixtureUser(userId, "hong@test.com");
      given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(user));
      given(mockTempPasswordRegistry.matches(userId, "wrong-password")).willReturn(false);

      // when & then
      assertThatThrownBy(() ->
          provider.authenticate(unauthenticatedToken("hong@test.com", "wrong-password")))
          .isInstanceOf(BadCredentialsException.class);

      verify(mockTempPasswordRegistry, never()).revoke(any());
    }
  }

  @Nested
  @DisplayName("인증 실패 - 계정 잠김")
  class AccountLocked {

    @Test
    @DisplayName("계정이 잠겨 있으면 임시 비밀번호를 확인하지 않고 바로 LockedException을 던진다")
    void authenticate_lockedAccount_throwsLockedExceptionWithoutCheckingTempPassword() {
      // given
      UUID userId = UUID.randomUUID();
      User lockedUser = fixtureUser(userId, "hong@test.com");
      lockedUser.lock(LockReason.ADMIN_ACTION);
      given(mockUserRepository.findByEmail("hong@test.com")).willReturn(Optional.of(lockedUser));

      // when & then
      assertThatThrownBy(() ->
          provider.authenticate(unauthenticatedToken("hong@test.com", "temp-password!")))
          .isInstanceOf(LockedException.class);

      verify(mockTempPasswordRegistry, never()).matches(any(), any());
    }
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("UsernamePasswordAuthenticationToken은 지원한다")
    void supports_usernamePasswordAuthenticationToken_returnsTrue() {
      assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("그 외 Authentication 구현체는 지원하지 않는다")
    void supports_otherAuthenticationType_returnsFalse() {
      assertThat(provider.supports(TestingAuthenticationToken.class)).isFalse();
    }
  }
}
