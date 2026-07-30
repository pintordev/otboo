package com.sprint.mission.otboo.domain.authuser.user.mapper;

import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserDto userDtoFrom(User user) {
    return new UserDto(
        user.getId(),
        user.getCreatedAt(),
        user.getEmail(),
        user.getName(),
        user.getRole(),
        user.isLocked()
    );
  }

  public UserDto userDtoFrom(CustomUserDetails principal) {
    return new UserDto(
        principal.getUserId(),
        principal.getCreatedAt(),
        principal.getEmail(),
        principal.getName(),
        principal.getRole(),
        principal.isLocked()
    );
  }
}
