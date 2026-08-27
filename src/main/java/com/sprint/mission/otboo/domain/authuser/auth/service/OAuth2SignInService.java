package com.sprint.mission.otboo.domain.authuser.auth.service;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.auth.exception.AccountLockedException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.EmailAlreadyRegisteredException;
import com.sprint.mission.otboo.domain.authuser.auth.exception.SocialAccountAlreadyLinkedException;
import com.sprint.mission.otboo.domain.authuser.auth.mapper.AuthMapper;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.SocialAccount;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.OAuth2Provider;
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
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OAuth2SignInService {

  private static final String UQ_USERS_EMAIL = "uq_users_email";
  private static final String UQ_SOCIAL_ACCOUNTS_PROVIDER_PROVIDER_ID =
      "uq_social_accounts_provider_provider_id";

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final UserSessionRegistry userSessionRegistry;
  private final TokenProvider tokenProvider;
  private final AuthMapper authMapper;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  @Transactional
  public SignInDto signIn(OAuth2Provider provider, String providerId, String providerEmail,
      String providerName, UUID explicitLinkUserId) {

    User user = findLinkedUser(provider, providerId)
        .orElseGet(() -> explicitLinkUserId != null
            ? linkToLoggedInUser(explicitLinkUserId, provider, providerId, providerEmail)
            : resolveAnonymousUser(provider, providerId, providerEmail, providerName));

    if (user.isLocked()) {
      throw AccountLockedException.withNone();
    }

    Instant now = Instant.now(clock);
    UserSession issued = userSessionRegistry.issue(user.getId(), now);
    String accessToken = tokenProvider.createAccessToken(user.getId(), issued.sessionId(),
        user.getRole().name(), now);
    String refreshToken = tokenProvider.createRefreshToken(user.getId(), issued.sessionId(),
        issued.currentRefreshJti(), now);

    return authMapper.signInDtoFrom(user, accessToken, refreshToken);
  }

  private Optional<User> findLinkedUser(OAuth2Provider provider, String providerId) {
    return socialAccountRepository.findByProviderAndProviderId(provider, providerId)
        .map(SocialAccount::getUser);
  }

  // 연동 전용 진입점(-link) - 비밀번호는 안 건드리고 현재 로그인된 사용자에게 그대로 연동.
  private User linkToLoggedInUser(UUID linkingUserId, OAuth2Provider provider, String providerId,
      String email) {
    userRepository.findByEmail(email)
        .filter(owner -> !owner.getId().equals(linkingUserId))
        .ifPresent(owner -> {
          throw EmailAlreadyRegisteredException.withNone();
        });

    User linkingUser = userRepository.findById(linkingUserId)
        .orElseThrow(UserNotFoundException::withNone);
    saveSocialAccountLink(linkingUser, provider, providerId, email);
    return linkingUser;
  }

  // 로그인 화면에서의 익명 시도 - 이메일이 이미 가입되어 있으면 병합, 없으면 새로 만든다.
  private User resolveAnonymousUser(OAuth2Provider provider, String providerId, String email,
      String name) {
    return userRepository.findByEmail(email)
        .map(existingUser -> mergeWithPasswordInvalidation(existingUser, provider, providerId,
            email))
        .orElseGet(() -> createSocialUser(provider, providerId, email, name));
  }

  // 익명 방문자의 소셜 이메일이 이미 가입된 로컬 계정과 같은 경우 - 연동 + 비밀번호 무효화.
  // 비밀번호를 그대로 두면 공격자가 피해자 이메일로 먼저 로컬 가입한 뒤 소셜 로그인으로
  // 계정을 가로챌 수 있어서, 병합 시점에 기존 비밀번호를 무작위 값으로 덮어쓰고 세션을 회수한다.
  private User mergeWithPasswordInvalidation(User existingUser, OAuth2Provider provider,
      String providerId, String email) {
    existingUser.changePassword(passwordEncoder.encode(UUID.randomUUID().toString()));
    userSessionRegistry.revokeAll(existingUser.getId());
    saveSocialAccountLink(existingUser, provider, providerId, email);
    return existingUser;
  }

  // 같은 (provider, providerId) 연동 요청이 동시에 들어오면 findLinkedUser가 둘 다
  // "아직 연동 안 됨"으로 보고 통과시킬 수 있어서, 유니크 제약 위반을 여기서 붙잡아
  // 이미 연동된 것으로 처리한다.
  private void saveSocialAccountLink(User user, OAuth2Provider provider, String providerId,
      String email) {
    try {
      socialAccountRepository.saveAndFlush(SocialAccount.link(user, provider, providerId, email));
    } catch (DataIntegrityViolationException e) {
      if (isSocialAccountUniqueViolation(e)) {
        throw SocialAccountAlreadyLinkedException.withCause(e);
      }
      throw e;
    }
  }

  private User createSocialUser(OAuth2Provider provider, String providerId, String email,
      String name) {
    User newUser = User.create(name, email, passwordEncoder.encode(UUID.randomUUID().toString()));

    User savedUser;
    try {
      savedUser = userRepository.saveAndFlush(newUser);
    } catch (DataIntegrityViolationException e) {
      // findByEmail 확인과 저장 사이에 다른 요청이 같은 이메일로 먼저 가입한 경우.
      if (isEmailUniqueViolation(e)) {
        throw DuplicateEmailException.withEmail(email, e);
      }
      throw e;
    }

    profileRepository.save(Profile.create(savedUser));
    socialAccountRepository.save(SocialAccount.link(savedUser, provider, providerId, email));
    return savedUser;
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cve
        && UQ_USERS_EMAIL.equalsIgnoreCase(cve.getConstraintName());
  }

  private boolean isSocialAccountUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cve
        && UQ_SOCIAL_ACCOUNTS_PROVIDER_PROVIDER_ID.equalsIgnoreCase(cve.getConstraintName());
  }
}
