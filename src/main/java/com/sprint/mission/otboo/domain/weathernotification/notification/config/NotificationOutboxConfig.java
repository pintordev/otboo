package com.sprint.mission.otboo.domain.weathernotification.notification.config;

import com.sprint.mission.otboo.domain.weathernotification.notification.properties.NotificationOutboxRelayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationOutboxRelayProperties.class)
public class NotificationOutboxConfig {

}