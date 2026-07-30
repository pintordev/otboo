package com.sprint.mission.otboo.domain.authuser.auth.mapper;

import com.sprint.mission.otboo.domain.authuser.auth.dto.response.JwtDto;
import com.sprint.mission.otboo.domain.authuser.auth.dto.response.SignInDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.mapper.UserMapper;
import com.sprint.mission.otboo.global.security.details.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthMapper {

  private final UserMapper userMapper;

  public SignInDto signInDtoFrom(CustomUserDetails principal, String accessToken,
      String refreshToken) {
    return new SignInDto(
        jwtDtoFrom(principal, accessToken),
        refreshToken
    );
  }

  public JwtDto jwtDtoFrom(CustomUserDetails principal, String accessToken) {
    return new JwtDto(
        userMapper.userDtoFrom(principal),
        accessToken
    );
  }
}
