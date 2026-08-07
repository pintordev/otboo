package com.sprint.mission.otboo.domain.authuser.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignInRequest(
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
    String username,

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(max = 13, message = "비밀번호는 13자 이하여야 합니다.")
    String password
) {

}
