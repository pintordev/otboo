package com.sprint.mission.otboo.global.security.jwt.filter;

import java.util.UUID;

public record UserPrincipal(
    UUID userId,
    String role
) {

}
