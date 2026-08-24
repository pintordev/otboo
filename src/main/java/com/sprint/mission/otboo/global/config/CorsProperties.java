package com.sprint.mission.otboo.global.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "otboo.cors")
@Validated
public record CorsProperties(
    @NotEmpty(message = "allowed-origins는 필수 값입니다.") String[] allowedOrigins
) {

}
