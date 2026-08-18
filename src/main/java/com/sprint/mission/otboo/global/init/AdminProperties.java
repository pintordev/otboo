package com.sprint.mission.otboo.global.init;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "otboo.admin")
@Validated
public record AdminProperties(
    @NotBlank(message = "name은 필수 값입니다.") String name,
    @NotBlank(message = "email은 필수 값입니다.") String email,
    @NotBlank(message = "password는 필수 값입니다.") String password
) {

}
