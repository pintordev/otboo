package com.sprint.mission.otboo.global.temppassword.config;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordProperties;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.global.temppassword.registry.impl.TempPasswordRedisRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(TempPasswordProperties.class)
public class TempPasswordRegistryConfig {

  @Bean
  public TempPasswordRegistry tempPasswordRegistry(
      StringRedisTemplate redisTemplate,
      TempPasswordProperties tempPasswordProperties,
      TempPasswordGenerator tempPasswordGenerator,
      PasswordEncoder passwordEncoder
  ) {
    return switch (tempPasswordProperties.impl()) {
      case REDIS -> new TempPasswordRedisRegistry(
          redisTemplate,
          tempPasswordProperties,
          tempPasswordGenerator,
          passwordEncoder
      );
    };
  }
}
