package com.sprint.mission.otboo.domain.authuser.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

  private static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  private AdminService adminService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserSessionRegistry userSessionRegistry;

  @Mock
  private UserMapper userMapper;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Nested
  @DisplayName("사용자 목록 조회 (searchUserList)")
  class SearchUserList {

    @Test
    @DisplayName("조건에 맞는 사용자 목록을 페이지로 반환한다")
    void 조건에_맞는_사용자_목록을_페이지로_반환한다() {
      // given
      UserListParams condition = new UserListParams(null, null, 10, "email",
          SortDirection.ASCENDING, null, null, null);
      CursorPageResponse<UserDto> expected = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "email", SortDirection.ASCENDING);
      given(userRepository.search(condition)).willReturn(expected);

      // when
      CursorPageResponse<UserDto> result = adminService.searchUserList(condition);

      // then
      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("역할 변경 (changeRole)")
  class ChangeRole {

    @Test
    @DisplayName("역할을 변경하고 해당 사용자의 모든 세션을 회수한다")
    void 역할을_변경하고_해당_사용자의_모든_세션을_회수한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);

      UserDto expected = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), Role.ADMIN, false);
      given(userMapper.userDtoFrom(user)).willReturn(expected);

      // when
      UserDto result = adminService.changeRole(user.getId(), request);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(user.getRole()).isEqualTo(Role.ADMIN);
      verify(userSessionRegistry).revokeAll(user.getId());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 UserNotFoundException을 던진다")
    void 존재하지_않는_사용자면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      given(userRepository.findById(userId)).willReturn(Optional.empty());
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);

      // when & then
      assertThatThrownBy(() -> adminService.changeRole(userId, request))
          .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("역할을 변경하면 대상 유저에게 WARNING 알림 이벤트를 발행한다")
    void 역할을_변경하면_대상_유저에게_WARNING_알림_이벤트를_발행한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
      UserRoleUpdateRequest request = fm.giveMeBuilder(UserRoleUpdateRequest.class)
          .set("role", Role.ADMIN)
          .sample();

      UserDto expected = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), Role.ADMIN, false);
      given(userMapper.userDtoFrom(user)).willReturn(expected);

      // when
      adminService.changeRole(user.getId(), request);

      // then
      ArgumentCaptor<NotificationRequestedEvent> eventCaptor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      NotificationRequestedEvent event = eventCaptor.getValue();
      assertThat(event.receiverIds()).containsExactly(user.getId());
      assertThat(event.content()).contains("ADMIN");
      assertThat(event.level()).isEqualTo(NotificationLevel.WARNING);
    }
  }

  @Nested
  @DisplayName("잠금 상태 변경 (changeLock)")
  class ChangeLock {

    @Test
    @DisplayName("locked가 true면 계정을 잠그고 모든 세션을 회수한다")
    void locked가_true면_계정을_잠그고_모든_세션을_회수한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);

      UserDto expected = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), user.getRole(), true);
      given(userMapper.userDtoFrom(user)).willReturn(expected);

      // when
      UserDto result = adminService.changeLock(user.getId(), request);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(user.isLocked()).isTrue();
      verify(userSessionRegistry).revokeAll(user.getId());
    }

    @Test
    @DisplayName("locked가 false면 계정 잠금을 해제하고 모든 세션을 회수한다")
    void locked가_false면_계정_잠금을_해제하고_모든_세션을_회수한다() {
      // given
      User user = User.create("홍길동", "hong@test.com", "encoded-password");
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
      UserLockUpdateRequest request = new UserLockUpdateRequest(false);

      UserDto expected = new UserDto(user.getId(), user.getCreatedAt(), user.getEmail(),
          user.getName(), user.getRole(), false);
      given(userMapper.userDtoFrom(user)).willReturn(expected);

      // when
      UserDto result = adminService.changeLock(user.getId(), request);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(user.isLocked()).isFalse();
      verify(userSessionRegistry).revokeAll(user.getId());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 UserNotFoundException을 던진다")
    void 존재하지_않는_사용자면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      given(userRepository.findById(userId)).willReturn(Optional.empty());
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);

      // when & then
      assertThatThrownBy(() -> adminService.changeLock(userId, request))
          .isInstanceOf(UserNotFoundException.class);
    }
  }
}
