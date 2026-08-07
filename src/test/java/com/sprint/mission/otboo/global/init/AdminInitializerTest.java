package com.sprint.mission.otboo.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInitializer")
class AdminInitializerTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private AdminInitializer adminInitializer(AdminProperties properties) {
    return new AdminInitializer(userRepository, profileRepository, passwordEncoder, properties);
  }

  private AdminProperties defaultAdminProperties() {
    return new AdminProperties("최고 관리자", "admin@test.com", "admin-password1");
  }

  @Nested
  @DisplayName("관리자 계정 초기화 - run")
  class Run {

    @Test
    @DisplayName("비밀번호가 설정되지 않았으면 아무것도 하지 않고 건너뛴다")
    void 비밀번호가_설정되지_않았으면_아무것도_하지_않고_건너뛴다() throws Exception {
      // given
      AdminProperties properties = new AdminProperties("최고 관리자", "admin@test.com", "   ");
      AdminInitializer initializer = adminInitializer(properties);

      // when
      initializer.run(null);

      // then
      verify(userRepository, never()).existsByEmail(any());
      verify(userRepository, never()).saveAndFlush(any());
      verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 동일한 이메일의 관리자 계정이 존재하면 새로 만들지 않는다")
    void 이미_동일한_이메일의_관리자_계정이_존재하면_새로_만들지_않는다() throws Exception {
      // given
      AdminProperties properties = defaultAdminProperties();
      AdminInitializer initializer = adminInitializer(properties);
      given(userRepository.existsByEmail(properties.email())).willReturn(true);

      // when
      initializer.run(null);

      // then
      verify(userRepository, never()).saveAndFlush(any());
      verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자 계정이 없으면 비밀번호를 암호화해서 관리자 User와 기본 Profile을 생성한다")
    void 관리자_계정이_없으면_비밀번호를_암호화해서_관리자_User와_기본_Profile을_생성한다() throws Exception {
      // given
      AdminProperties properties = defaultAdminProperties();
      AdminInitializer initializer = adminInitializer(properties);
      given(userRepository.existsByEmail(properties.email())).willReturn(false);
      given(passwordEncoder.encode("admin-password1")).willReturn("encoded-admin-password");

      // when
      initializer.run(null);

      // then
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).saveAndFlush(userCaptor.capture());
      User savedAdmin = userCaptor.getValue();
      assertThat(savedAdmin.getName()).isEqualTo("최고 관리자");
      assertThat(savedAdmin.getEmail()).isEqualTo("admin@test.com");
      assertThat(savedAdmin.getPassword()).isEqualTo("encoded-admin-password");
      assertThat(savedAdmin.getRole()).isEqualTo(Role.ADMIN);

      ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
      verify(profileRepository).save(profileCaptor.capture());
      assertThat(profileCaptor.getValue().getUser()).isEqualTo(savedAdmin);
    }

    @Test
    @DisplayName("existsByEmail 통과 후 동시 생성으로 유니크 제약 위반이 나면 예외를 전파하지 않고 Profile도 생성하지 않는다")
    void existsByEmail_통과_후_동시_생성으로_유니크_제약_위반이_나면_예외를_전파하지_않고_Profile도_생성하지_않는다()
        throws Exception {
      // given
      AdminProperties properties = defaultAdminProperties();
      AdminInitializer initializer = adminInitializer(properties);
      given(userRepository.existsByEmail(properties.email())).willReturn(false);
      given(passwordEncoder.encode(any())).willReturn("encoded-admin-password");
      given(userRepository.saveAndFlush(any(User.class)))
          .willThrow(
              new DataIntegrityViolationException("duplicate key value violates unique constraint"));

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
      verify(profileRepository, never()).save(any());
    }
  }
}
