package com.sprint.mission.otboo.global.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sprint.mission.otboo.external")
public class FeignConfig {

}