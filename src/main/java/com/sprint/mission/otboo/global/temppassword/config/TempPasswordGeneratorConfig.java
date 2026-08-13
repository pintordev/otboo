package com.sprint.mission.otboo.global.temppassword.config;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.generator.impl.RandomTempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TempPasswordProperties.class})
public class TempPasswordGeneratorConfig {

  @Bean
  public TempPasswordGenerator tempPasswordGenerator(
      TempPasswordProperties tempPasswordProperties) {
    return switch (tempPasswordProperties.generator()) {
      case RANDOM -> new RandomTempPasswordGenerator();
    };
  }
}
