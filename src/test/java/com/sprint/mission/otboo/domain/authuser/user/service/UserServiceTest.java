package com.sprint.mission.otboo.domain.authuser.user.service;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserCreateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.DuplicateEmailException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  UserService userService;
  @Mock
  UserRepository mockUserRepository;
  @Mock
  ProfileRepository mockProfileRepository;
  @Mock
  PasswordEncoder mockPasswordEncoder;
  @Spy
  UserMapper userMapper = new UserMapper();

  @Nested
  @DisplayName("회원가입 성공")
  class SignUpSuccess {

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 User, Profile을 저장한 뒤 UserDto를 반환한다")
    void signUp_success() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(request.password())).willReturn("encoded-password");

      User savedUser = User.create(request.name(), request.email(), "encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

      Profile savedProfile = Profile.createDefault(savedUser);
      given(mockProfileRepository.save(any(Profile.class))).willReturn(savedProfile);

      // when
      UserDto result = userService.signUp(request);

      // then
      assertThat(result.id()).isEqualTo(savedUser.getId());
      assertThat(result.createdAt()).isEqualTo(savedUser.getCreatedAt());
      assertThat(result.email()).isEqualTo(savedUser.getEmail());
      assertThat(result.name()).isEqualTo(savedUser.getName());
      assertThat(result.role()).isEqualTo(savedUser.getRole());
      assertThat(result.locked()).isEqualTo(savedUser.isLocked());
      verify(mockUserRepository).existsByEmail(request.email());
      verify(mockPasswordEncoder).encode(request.password());
      verify(mockUserRepository).saveAndFlush(any(User.class));
      verify(mockProfileRepository).save(any(Profile.class));
    }

    @Test
    @DisplayName("회원가입 시 평문 비밀번호가 아닌 암호화된 비밀번호로 User를 생성한다")
    void signUp_encodesPasswordBeforeCreatingUser() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(request.password())).willReturn("encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(mockProfileRepository.save(any(Profile.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // when
      userService.signUp(request);

      // then
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(mockUserRepository).saveAndFlush(userCaptor.capture());
      User capturedUser = userCaptor.getValue();

      assertThat(capturedUser.getPassword()).isEqualTo("encoded-password");
      assertThat(capturedUser.getPassword()).isNotEqualTo(request.password());
    }

    @Test
    @DisplayName("회원가입 시 기본 권한은 USER, 잠김 상태는 false로 생성된다")
    void signUp_createsUserWithDefaultRoleAndUnlocked() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(any())).willReturn("encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(mockProfileRepository.save(any(Profile.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // when
      userService.signUp(request);

      // then
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(mockUserRepository).saveAndFlush(userCaptor.capture());
      User capturedUser = userCaptor.getValue();

      assertThat(capturedUser.getRole()).isEqualTo(Role.USER);
      assertThat(capturedUser.isLocked()).isFalse();
    }

    @Test
    @DisplayName("회원가입 시 저장된 User와 연결된 기본 Profile을 생성한다")
    void signUp_createsDefaultProfileLinkedToUser() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      User savedUser = User.create(request.name(), request.email(), "encoded-password");

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(any())).willReturn("encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class))).willReturn(savedUser);
      given(mockProfileRepository.save(any(Profile.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // when
      userService.signUp(request);

      // then
      ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
      verify(mockProfileRepository).save(profileCaptor.capture());
      Profile capturedProfile = profileCaptor.getValue();

      assertThat(capturedProfile.getUser()).isEqualTo(savedUser);
    }

    @Test
    @DisplayName("회원가입은 existsByEmail을 정확히 1회 호출한다")
    void signUp_checksEmailDuplicationExactlyOnce() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(any())).willReturn("encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(mockProfileRepository.save(any(Profile.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      // when
      userService.signUp(request);

      // then
      verify(mockUserRepository, times(1)).existsByEmail(request.email());
    }
  }

  @Nested
  @DisplayName("이메일 중복")
  class SignUpDuplicateEmail {

    @Test
    @DisplayName("이미 가입된 이메일로 회원가입 시도 시 예외가 발생하고 저장은 일어나지 않는다")
    void signUp_duplicateEmail_throwsExceptionAndDoesNotSave() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(true);

      // when & then
      assertThatThrownBy(() -> userService.signUp(request))
          .isInstanceOf(DuplicateEmailException.class);

      verify(mockUserRepository, never()).saveAndFlush(any());
      verify(mockProfileRepository, never()).save(any());
      verify(mockPasswordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("existsByEmail 체크를 통과했지만 저장 시점에 동시성으로 인해 중복 제약이 걸리면 예외가 발생하고 Profile은 저장되지 않는다")
    void signUp_raceConditionOnSave_throwsExceptionAndDoesNotSaveProfile() {
      // given
      UserCreateRequest request = fixtureMonkey.giveMeBuilder(UserCreateRequest.class).sample();

      given(mockUserRepository.existsByEmail(request.email())).willReturn(false);
      given(mockPasswordEncoder.encode(any())).willReturn("encoded-password");
      given(mockUserRepository.saveAndFlush(any(User.class)))
          .willThrow(new DataIntegrityViolationException(
              "duplicate key value violates unique constraint"));

      // when & then
      assertThatThrownBy(() -> userService.signUp(request))
          .isInstanceOf(RuntimeException.class);

      verify(mockProfileRepository, never()).save(any());
    }
  }
}
