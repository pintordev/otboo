package com.sprint.mission.otboo.domain.authuser.user.dto.response;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    Instant createdAt,
    String email,
    String name,
    Role role,
    boolean locked
) {

}
