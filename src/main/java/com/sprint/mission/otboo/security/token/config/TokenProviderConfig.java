package com.sprint.mission.otboo.security.token.config;

import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.token.provider.impl.JjwtTokenProvider;
import com.sprint.mission.otboo.security.token.provider.impl.NimbusTokenProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenProviderConfig {

  @Bean
  @ConditionalOnProperty(name = "otboo.security.token.impl", havingValue = "nimbus", matchIfMissing = true)
  public TokenProvider nimbusTokenProvider(TokenProperties tokenProperties, Clock clock) {
    return new NimbusTokenProvider(tokenProperties, clock);
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.security.token.impl", havingValue = "jjwt")
  public TokenProvider jjwtTokenProvider(TokenProperties tokenProperties, Clock clock) {
    return new JjwtTokenProvider(tokenProperties, clock);
  }
}
