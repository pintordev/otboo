package com.sprint.mission.otboo.domain.authuser.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.EmailAlreadyRegisteredException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.SocialAccountAlreadyLinkedException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.SocialAccount;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.OAuth2Provider;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.SocialAccountRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2SignInService")
class OAuth2SignInServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @InjectMocks
  private OAuth2SignInService oAuth2SignInService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private SocialAccountRepository socialAccountRepository;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  @Mock
  private TokenProvider tokenProvider;

  @Mock
  private AuthMapper authMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private Clock clock;

  // Hibernate는 persist 시점에 id를 채우는데, 이 테스트는 실제 저장을 하지 않으니
  // findById/findByProviderAndProviderId로 되돌려줄 User가 어떤 id를 갖는지는 직접 넣어줘야 한다.
  private User userWithId(UUID id, String name, String email) {
    User user = User.create(name, email, "encoded-password");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private DataIntegrityViolationException uniqueViolation(String constraintName) {
    ConstraintViolationException cause =
        new ConstraintViolationException("unique violation", null, constraintName);
    return new DataIntegrityViolationException("could not execute statement", cause);
  }

  @Nested
  @DisplayName("기존 연동된 소셜 계정으로 로그인")
  class LoginWithLinkedAccount {

    @Test
    @DisplayName("이미 연동된 소셜 계정이면 기존 사용자로 로그인해 토큰을 발급한다")
    void 이미_연동된_소셜_계정이면_기존_사용자로_로그인해_토큰을_발급한다() {
      // given
      User existingUser = userWithId(UUID.randomUUID(), "홍길동", "hong@test.com");
      SocialAccount linkedAccount = SocialAccount.link(existingUser, OAuth2Provider.GOOGLE,
          "google-sub-1", "hong@gmail.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-sub-1"))
          .willReturn(Optional.of(linkedAccount));

      given(clock.instant()).willReturn(NOW);
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(existingUser.getId(), sessionId, jti, NOW);
      given(userSessionRegistry.issue(existingUser.getId(), NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(existingUser.getId(), sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(existingUser.getId(), sessionId, jti, NOW))
          .willReturn("refresh-token");

      UserDto userDto = new UserDto(existingUser.getId(), null, "hong@test.com", "홍길동", Role.USER,
          false);
      SignInDto expected = new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token");
      given(authMapper.signInDtoFrom(existingUser, "access-token", "refresh-token"))
          .willReturn(expected);

      // when
      SignInDto result = oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-sub-1", "hong@gmail.com", "홍길동", null);

      // then
      assertThat(result).isEqualTo(expected);
      verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("연동된 계정이 잠겨있으면 AccountLockedException을 던진다")
    void 연동된_계정이_잠겨있으면_AccountLockedException을_던진다() {
      // given
      User lockedUser = userWithId(UUID.randomUUID(), "홍길동", "hong@test.com");
      lockedUser.lock(LockReason.ADMIN_ACTION);
      SocialAccount linkedAccount = SocialAccount.link(lockedUser, OAuth2Provider.KAKAO,
          "1234567", "user_1234567@kakao.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.KAKAO, "1234567"))
          .willReturn(Optional.of(linkedAccount));

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "1234567", "user_1234567@kakao.com", "kakao-user", null))
          .isInstanceOf(AccountLockedException.class);
      verify(userSessionRegistry, never()).issue(any(), any());
    }
  }

  @Nested
  @DisplayName("계정 연동 (-link 진입점)")
  class ExplicitLink {

    @Test
    @DisplayName("로그인된 사용자에게 소셜 계정을 연동한다")
    void 로그인된_사용자에게_소셜_계정을_연동한다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      User linkingUser = userWithId(linkingUserId, "홍길동", "hong@test.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.KAKAO, "kakao-99"))
          .willReturn(Optional.empty());
      given(userRepository.findById(linkingUserId)).willReturn(Optional.of(linkingUser));

      given(clock.instant()).willReturn(NOW);
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(linkingUserId, sessionId, jti, NOW);
      given(userSessionRegistry.issue(linkingUserId, NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(linkingUserId, sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(linkingUserId, sessionId, jti, NOW))
          .willReturn("refresh-token");
      UserDto userDto = new UserDto(linkingUserId, null, "hong@test.com", "홍길동", Role.USER, false);
      given(authMapper.signInDtoFrom(linkingUser, "access-token", "refresh-token"))
          .willReturn(new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token"));

      // when
      oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "kakao-99", "hong@gmail.com", "카카오닉네임", linkingUserId);

      // then
      ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
      verify(socialAccountRepository).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getUser()).isEqualTo(linkingUser);
      assertThat(captor.getValue().getProvider()).isEqualTo(OAuth2Provider.KAKAO);
      assertThat(captor.getValue().getProviderId()).isEqualTo("kakao-99");
      assertThat(captor.getValue().getProviderEmail()).isEqualTo("hong@gmail.com");
      verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("연동 요청이 동시에 들어와 소셜 계정 유니크 제약을 위반하면 SocialAccountAlreadyLinkedException으로 변환한다")
    void 연동_요청이_동시에_들어와_소셜_계정_유니크_제약을_위반하면_SocialAccountAlreadyLinkedException으로_변환한다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      User linkingUser = userWithId(linkingUserId, "홍길동", "hong@test.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.KAKAO, "kakao-100"))
          .willReturn(Optional.empty());
      given(userRepository.findById(linkingUserId)).willReturn(Optional.of(linkingUser));
      given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
          .willThrow(uniqueViolation("uq_social_accounts_provider_provider_id"));

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "kakao-100", "hong@gmail.com", "카카오닉네임", linkingUserId))
          .isInstanceOf(SocialAccountAlreadyLinkedException.class);
    }

    @Test
    @DisplayName("소셜 계정 제약과 무관한 위반이면 예외를 변환하지 않고 그대로 전파한다")
    void 소셜_계정_제약과_무관한_위반이면_예외를_변환하지_않고_그대로_전파한다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      User linkingUser = userWithId(linkingUserId, "홍길동", "hong@test.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.KAKAO, "kakao-101"))
          .willReturn(Optional.empty());
      given(userRepository.findById(linkingUserId)).willReturn(Optional.of(linkingUser));
      DataIntegrityViolationException otherViolation = uniqueViolation("uq_other_constraint");
      given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
          .willThrow(otherViolation);

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "kakao-101", "hong@gmail.com", "카카오닉네임", linkingUserId))
          .isSameAs(otherViolation);
    }

    @Test
    @DisplayName("이미 다른 사용자가 사용 중인 이메일이면 EmailAlreadyRegisteredException을 던진다")
    void 이미_다른_사용자가_사용_중인_이메일이면_EmailAlreadyRegisteredException을_던진다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      User owner = userWithId(UUID.randomUUID(), "다른사람", "shared@gmail.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-2"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("shared@gmail.com")).willReturn(Optional.of(owner));

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-2", "shared@gmail.com", "홍길동", linkingUserId))
          .isInstanceOf(EmailAlreadyRegisteredException.class);
      verify(userRepository, never()).findById(any());
      verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("연동하려는 이메일이 이미 자기 자신의 이메일이면 예외 없이 연동된다")
    void 연동하려는_이메일이_이미_자기_자신의_이메일이면_예외_없이_연동된다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      User linkingUser = userWithId(linkingUserId, "홍길동", "hong@gmail.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-3"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("hong@gmail.com")).willReturn(Optional.of(linkingUser));
      given(userRepository.findById(linkingUserId)).willReturn(Optional.of(linkingUser));

      given(clock.instant()).willReturn(NOW);
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(linkingUserId, sessionId, jti, NOW);
      given(userSessionRegistry.issue(linkingUserId, NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(linkingUserId, sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(linkingUserId, sessionId, jti, NOW))
          .willReturn("refresh-token");
      UserDto userDto = new UserDto(linkingUserId, null, "hong@gmail.com", "홍길동", Role.USER, false);
      given(authMapper.signInDtoFrom(linkingUser, "access-token", "refresh-token"))
          .willReturn(new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token"));

      // when & then
      assertThatCode(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-3", "hong@gmail.com", "홍길동", linkingUserId))
          .doesNotThrowAnyException();
      verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    @DisplayName("로그인된 사용자를 찾을 수 없으면 UserNotFoundException을 던진다")
    void 로그인된_사용자를_찾을_수_없으면_UserNotFoundException을_던진다() {
      // given
      UUID linkingUserId = UUID.randomUUID();
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-6"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("hong@gmail.com")).willReturn(Optional.empty());
      given(userRepository.findById(linkingUserId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-6", "hong@gmail.com", "홍길동", linkingUserId))
          .isInstanceOf(UserNotFoundException.class);
      verify(socialAccountRepository, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("익명 로그인 시도 (자동 가입/병합)")
  class AnonymousMergeOrCreate {

    @Test
    @DisplayName("이메일이 이미 가입되어 있으면 비밀번호를 무효화하고 기존 계정에 연동한다")
    void 이메일이_이미_가입되어_있으면_비밀번호를_무효화하고_기존_계정에_연동한다() {
      // given
      User existingUser = userWithId(UUID.randomUUID(), "홍길동", "hong@gmail.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-4"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("hong@gmail.com")).willReturn(Optional.of(existingUser));
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");

      given(clock.instant()).willReturn(NOW);
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(existingUser.getId(), sessionId, jti, NOW);
      given(userSessionRegistry.issue(existingUser.getId(), NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(existingUser.getId(), sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(existingUser.getId(), sessionId, jti, NOW))
          .willReturn("refresh-token");
      UserDto userDto = new UserDto(existingUser.getId(), null, "hong@gmail.com", "홍길동", Role.USER,
          false);
      given(authMapper.signInDtoFrom(existingUser, "access-token", "refresh-token"))
          .willReturn(new SignInDto(new JwtDto(userDto, "access-token"), "refresh-token"));

      // when
      oAuth2SignInService.signIn(OAuth2Provider.GOOGLE, "google-4", "hong@gmail.com", "홍길동", null);

      // then
      assertThat(existingUser.getPassword()).isEqualTo("random-encoded-password");
      verify(userSessionRegistry).revokeAll(existingUser.getId());
      ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
      verify(socialAccountRepository).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getUser()).isEqualTo(existingUser);
      assertThat(captor.getValue().getProviderEmail()).isEqualTo("hong@gmail.com");
    }

    @Test
    @DisplayName("병합 연동 요청이 동시에 들어와 소셜 계정 유니크 제약을 위반하면 SocialAccountAlreadyLinkedException으로 변환한다")
    void 병합_연동_요청이_동시에_들어와_소셜_계정_유니크_제약을_위반하면_SocialAccountAlreadyLinkedException으로_변환한다() {
      // given
      User existingUser = userWithId(UUID.randomUUID(), "홍길동", "hong2@gmail.com");
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-9"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("hong2@gmail.com")).willReturn(Optional.of(existingUser));
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");
      given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
          .willThrow(uniqueViolation("uq_social_accounts_provider_provider_id"));

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-9", "hong2@gmail.com", "홍길동", null))
          .isInstanceOf(SocialAccountAlreadyLinkedException.class);
    }

    @Test
    @DisplayName("동시에 같은 이메일로 가입 시도해 무결성 예외가 발생하면 DuplicateEmailException으로 변환한다")
    void 동시에_같은_이메일로_가입_시도해_무결성_예외가_발생하면_DuplicateEmailException으로_변환한다() {
      // given
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-5"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("race@gmail.com")).willReturn(Optional.empty());
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");
      given(userRepository.saveAndFlush(any(User.class)))
          .willThrow(uniqueViolation("uq_users_email"));

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-5", "race@gmail.com", "홍길동", null))
          .isInstanceOf(DuplicateEmailException.class);
      verify(profileRepository, never()).save(any());
      verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일과 무관한 제약 위반이면 예외를 변환하지 않고 그대로 전파한다")
    void 이메일과_무관한_제약_위반이면_예외를_변환하지_않고_그대로_전파한다() {
      // given
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-8"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("other@gmail.com")).willReturn(Optional.empty());
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");
      DataIntegrityViolationException otherViolation = uniqueViolation("uq_other_constraint");
      given(userRepository.saveAndFlush(any(User.class))).willThrow(otherViolation);

      // when & then
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-8", "other@gmail.com", "홍길동", null))
          .isSameAs(otherViolation);
      verify(profileRepository, never()).save(any());
      verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("병합된 계정이 잠겨있으면 AccountLockedException을 던진다")
    void 병합된_계정이_잠겨있으면_AccountLockedException을_던진다() {
      // given
      User lockedUser = userWithId(UUID.randomUUID(), "홍길동", "locked@gmail.com");
      lockedUser.lock(LockReason.ADMIN_ACTION);
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, "google-7"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail("locked@gmail.com")).willReturn(Optional.of(lockedUser));
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");

      // when & then
      // 비밀번호 무효화/세션 회수는 잠금 체크보다 먼저 일어난다 - 로그인만 최종 거부됨
      assertThatThrownBy(() -> oAuth2SignInService.signIn(
          OAuth2Provider.GOOGLE, "google-7", "locked@gmail.com", "홍길동", null))
          .isInstanceOf(AccountLockedException.class);
      verify(userSessionRegistry).revokeAll(lockedUser.getId());
      verify(userSessionRegistry, never()).issue(any(), any());
    }

    @Test
    @DisplayName("이메일이 가입되어 있지 않으면 새 계정을 생성한다")
    void 이메일이_가입되어_있지_않으면_새_계정을_생성한다() {
      // given
      given(socialAccountRepository.findByProviderAndProviderId(OAuth2Provider.KAKAO, "kakao-1"))
          .willReturn(Optional.empty());
      given(passwordEncoder.encode(any())).willReturn("random-encoded-password");

      UUID newUserId = UUID.randomUUID();
      given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
        User newUser = invocation.getArgument(0);
        ReflectionTestUtils.setField(newUser, "id", newUserId);
        return newUser;
      });

      given(clock.instant()).willReturn(NOW);
      UUID sessionId = UUID.randomUUID();
      UUID jti = UUID.randomUUID();
      UserSession issued = new UserSession(newUserId, sessionId, jti, NOW);
      given(userSessionRegistry.issue(newUserId, NOW)).willReturn(issued);
      given(tokenProvider.createAccessToken(newUserId, sessionId, "USER", NOW))
          .willReturn("access-token");
      given(tokenProvider.createRefreshToken(newUserId, sessionId, jti, NOW))
          .willReturn("refresh-token");

      UserDto expectedUserDto = new UserDto(newUserId, null, "길동이_kakao-1@kakao.com", "길동이",
          Role.USER, false);
      SignInDto expected = new SignInDto(new JwtDto(expectedUserDto, "access-token"), "refresh-token");
      given(authMapper.signInDtoFrom(any(User.class), eq("access-token"), eq("refresh-token")))
          .willReturn(expected);

      // when
      SignInDto result = oAuth2SignInService.signIn(
          OAuth2Provider.KAKAO, "kakao-1", "길동이_kakao-1@kakao.com", "길동이", null);

      // then
      assertThat(result).isEqualTo(expected);
      verify(profileRepository).save(any(Profile.class));
    }
  }
}
