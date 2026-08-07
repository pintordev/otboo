package com.sprint.mission.otboo.domain.authuser.auth.dto.response;


public record RefreshDto(
    JwtDto jwtDto,
    String refreshToken
) {

}
