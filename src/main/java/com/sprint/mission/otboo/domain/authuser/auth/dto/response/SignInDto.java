package com.sprint.mission.otboo.domain.authuser.auth.dto.response;

public record SignInDto(
    JwtDto jwtDto,
    String refreshToken
) {

}
