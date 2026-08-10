package com.sprint.mission.otboo.domain.authuser.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.ChangePasswordRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.LocationRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.ProfileUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.ProfileDto;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Gender;
import com.sprint.mission.otboo.domain.authuser.user.exception.AccessDeniedException;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

  @InjectMocks
  private UserService userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private UserMapper userMapper;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  @Mock
  private TempPasswordRegistry tempPasswordRegistry;

  private DataIntegrityViolationException uniqueViolation() {
    ConstraintViolationException cause =
        new ConstraintViolationException("unique violation", null, "uq_users_email");
    return new DataIntegrityViolationException("could not execute statement", cause);
  }

  @Nested
  @DisplayName("회원가입 (signUp)")
  class SignUp {

    @Test
    @DisplayName("중복된 이메일이 아니면 사용자와 기본 프로필을 생성하고 UserDto를 반환한다")
    void 중복된_이메일이_아니면_사용자와_기본_프로필을_생성하고_UserDto를_반환한다() {
      // given
      UserCreateRequest request = new UserCreateRequest("hong@test.com", "password1", "홍길동");
      given(userRepository.existsByEmail("hong@test.com")).willReturn(false);
      given(passwordEncoder.encode("password1")).willReturn("encoded-password");
      given(userRepository.saveAndFlush(any(User.class))).willAnswer(
          invocation -> invocation.getArgument(0));

      UserDto expected = new UserDto(UUID.randomUUID(), null, "hong@test.com", "홍길동", null, false);
      given(userMapper.userDtoFrom(any(User.class))).willReturn(expected);

      // when
      UserDto result = userService.signUp(request);

      // then
      assertThat(result).isEqualTo(expected);
      verify(profileRepository).save(any(Profile.class));
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 DuplicateEmailException을 던진다")
    void 이미_존재하는_이메일이면_DuplicateEmailException을_던진다() {
      // given
      UserCreateRequest request = new UserCreateRequest("hong@test.com", "password1", "홍길동");
      given(userRepository.existsByEmail("hong@test.com")).willReturn(true);

      // when & then
      assertThatThrownBy(() -> userService.signUp(request))
          .isInstanceOf(DuplicateEmailException.class);
      verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("저장 시점에 유니크 제약이 위반되면 DuplicateEmailException을 던진다")
    void 저장_시점에_유니크_제약이_위반되면_DuplicateEmailException을_던진다() {
      // given: exists 체크는 통과했지만 동시 가입으로 유니크 제약 위반
      UserCreateRequest request = new UserCreateRequest("hong@test.com", "password1", "홍길동");
      given(userRepository.existsByEmail("hong@test.com")).willReturn(false);
      given(passwordEncoder.encode("password1")).willReturn("encoded-password");
      given(userRepository.saveAndFlush(any(User.class))).willThrow(uniqueViolation());

      // when & then
      assertThatThrownBy(() -> userService.signUp(request))
          .isInstanceOf(DuplicateEmailException.class);
      verify(profileRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("프로필 조회 (getProfile)")
  class GetProfile {

    @Test
    @DisplayName("프로필을 조회해서 반환한다")
    void 프로필을_조회해서_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      Profile profile = Profile.create(user);
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));

      ProfileDto expected = new ProfileDto(userId, "홍길동", null, null, null, 3, null);
      given(userMapper.profileDtoFrom(profile)).willReturn(expected);

      // when
      ProfileDto result = userService.getProfile(userId);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 UserNotFoundException을 던진다")
    void 존재하지_않는_사용자면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.getProfile(userId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("프로필 수정 (changeProfile)")
  class ChangeProfile {

    @Test
    @DisplayName("본인이면 이름과 프로필 정보를 수정하고 ProfileDto를 반환한다")
    void 본인이면_이름과_프로필_정보를_수정하고_ProfileDto를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      Profile profile = Profile.create(user);
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));

      LocationRequest locationRequest = new LocationRequest(37.5, 127.0, 60, 127, null);
      ProfileUpdateRequest request =
          new ProfileUpdateRequest("김철수", Gender.MALE, LocalDate.of(1995, 1, 1), locationRequest,
              4);
      Location location = Location.create(37.5, 127.0, 60, 127, null);
      given(userMapper.locationFrom(locationRequest)).willReturn(location);

      ProfileDto expected = new ProfileDto(userId, "김철수", Gender.MALE,
          LocalDate.of(1995, 1, 1), null, 4, null);
      given(userMapper.profileDtoFrom(profile)).willReturn(expected);

      // when
      ProfileDto result = userService.changeProfile(userId, request, null, userId);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(user.getName()).isEqualTo("김철수");
      assertThat(profile.getGender()).isEqualTo(Gender.MALE);
      assertThat(profile.getTemperatureSensitivity()).isEqualTo(4);
    }

    @Test
    @DisplayName("본인이 아니면 AccessDeniedException을 던진다")
    void 본인이_아니면_AccessDeniedException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID requestUserId = UUID.randomUUID();
      ProfileUpdateRequest request = new ProfileUpdateRequest("김철수", null, null, null, 3);

      // when & then
      assertThatThrownBy(() -> userService.changeProfile(userId, request, null, requestUserId))
          .isInstanceOf(AccessDeniedException.class);
      verify(profileRepository, never()).findByIdWithUser(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 UserNotFoundException을 던진다")
    void 존재하지_않는_사용자면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.empty());
      ProfileUpdateRequest request = new ProfileUpdateRequest("김철수", null, null, null, 3);

      // when & then
      assertThatThrownBy(() -> userService.changeProfile(userId, request, null, userId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("비밀번호 변경 (changePassword)")
  class ChangePassword {

    @Test
    @DisplayName("본인이면 비밀번호를 변경하고 모든 세션과 임시 비밀번호를 회수한다")
    void 본인이면_비밀번호를_변경하고_모든_세션과_임시_비밀번호를_회수한다() {
      // given
      UUID userId = UUID.randomUUID();
      User user = User.create("홍길동", "hong@test.com", "old-encoded-password");
      given(userRepository.findById(userId)).willReturn(Optional.of(user));
      given(passwordEncoder.encode("new-password1")).willReturn("new-encoded-password");

      ChangePasswordRequest request = new ChangePasswordRequest("new-password1");

      // when
      userService.changePassword(userId, request, userId);

      // then
      assertThat(user.getPassword()).isEqualTo("new-encoded-password");
      verify(userSessionRegistry).revokeAll(userId);
      verify(tempPasswordRegistry).revoke(userId);
    }

    @Test
    @DisplayName("본인이 아니면 AccessDeniedException을 던진다")
    void 본인이_아니면_AccessDeniedException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID requestUserId = UUID.randomUUID();
      ChangePasswordRequest request = new ChangePasswordRequest("new-password1");

      // when & then
      assertThatThrownBy(() -> userService.changePassword(userId, request, requestUserId))
          .isInstanceOf(AccessDeniedException.class);
      verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 UserNotFoundException을 던진다")
    void 존재하지_않는_사용자면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      given(userRepository.findById(userId)).willReturn(Optional.empty());
      ChangePasswordRequest request = new ChangePasswordRequest("new-password1");

      // when & then
      assertThatThrownBy(() -> userService.changePassword(userId, request, userId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }
}
