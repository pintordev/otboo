package com.sprint.mission.otboo.domain.authuser.auth.dto.response;

import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;

public record JwtDto(
    UserDto userDto,
    String accessToken
) {

}
