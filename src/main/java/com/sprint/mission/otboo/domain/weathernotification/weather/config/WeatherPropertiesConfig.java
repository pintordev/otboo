package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LocationBlockProperties.class})
public class WeatherPropertiesConfig {

}