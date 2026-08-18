package com.sprint.mission.otboo.domain.authuser.user.service;

import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserLockUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserRoleUpdateRequest;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.LockReason;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminService {

  private final UserRepository userRepository;
  private final UserSessionRegistry userSessionRegistry;
  private final UserMapper userMapper;
  private final ApplicationEventPublisher eventPublisher;

  public CursorPageResponse<UserDto> searchUserList(UserListParams condition) {
    return userRepository.search(condition);
  }

  @Transactional
  public UserDto changeRole(UUID userId, UserRoleUpdateRequest request) {
    User foundUser = getFoundUser(userId);

    foundUser.changeRole(request.role());

    userSessionRegistry.revokeAll(userId);

    eventPublisher.publishEvent(new NotificationRequestedEvent(
        Set.of(userId), "권한 변경",
        request.role() + " 권한으로 변경되었습니다.", NotificationLevel.WARNING));

    return userMapper.userDtoFrom(foundUser);
  }

  @Transactional
  public UserDto changeLock(UUID userId, UserLockUpdateRequest request) {
    User foundUser = getFoundUser(userId);

    if (request.locked()) {
      foundUser.lock(LockReason.ADMIN_ACTION);
    } else {
      foundUser.unlock();
    }

    userSessionRegistry.revokeAll(userId);

    return userMapper.userDtoFrom(foundUser);
  }

  private User getFoundUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::withNone);
  }
}
