package com.sprint.mission.otboo.security.config;

import com.sprint.mission.otboo.global.init.AdminProperties;
import com.sprint.mission.otboo.security.cookie.properties.RefreshTokenCookieProperties;
import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    TokenProperties.class,
    UserSessionProperties.class,
    RefreshTokenCookieProperties.class,
    AdminProperties.class
})
public class SecurityPropertiesConfig {

}
