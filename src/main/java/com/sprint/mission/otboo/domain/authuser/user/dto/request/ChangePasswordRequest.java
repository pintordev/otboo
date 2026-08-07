package com.sprint.mission.otboo.domain.authuser.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 6, max = 13, message = "비밀번호는 6자 이상 13자 이하여야 합니다.")
    String password
) {

}
