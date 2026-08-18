package com.sprint.mission.otboo.security.oauth2.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.security.oauth2")
public record OAuth2Properties(
    @NotBlank(message = "successRedirectUri는 필수 값입니다.") String successRedirectUri,
    @NotBlank(message = "failureRedirectUri는 필수 값입니다.") String failureRedirectUri
) {

}
