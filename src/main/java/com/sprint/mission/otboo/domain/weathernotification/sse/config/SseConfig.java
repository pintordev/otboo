package com.sprint.mission.otboo.domain.weathernotification.sse.config;

import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SseReplayBufferProperties.class)
public class SseConfig {

}