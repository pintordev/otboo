package com.sprint.mission.otboo.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String accessSecret,
    String refreshSecret,
    long accessTokenExpirationMinutes,
    long refreshTokenExpirationDays
) {

}
