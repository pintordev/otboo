package com.sprint.mission.otboo.global.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
    boolean secure
) {

}
