package com.sprint.mission.otboo.security.cookie.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.security.refresh-cookie")
public record RefreshTokenCookieProperties(

    @NotNull(message = "refreshTokenExpiration은 필수 값입니다.")
    @DurationMin(seconds = 1, message = "refreshTokenExpiration은 최소 1초 이상이어야 합니다.")
    Duration refreshTokenExpiration,

    @NotNull(message = "secure는 필수 값입니다.")
    Boolean secure
) {

}
