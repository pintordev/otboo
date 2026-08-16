package com.sprint.mission.otboo.security.token.config;

import com.sprint.mission.otboo.security.token.properties.TokenProperties;
import com.sprint.mission.otboo.security.token.provider.TokenProvider;
import com.sprint.mission.otboo.security.token.provider.impl.JjwtTokenProvider;
import com.sprint.mission.otboo.security.token.provider.impl.NimbusTokenProvider;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenProviderConfig {

  @Bean
  public TokenProvider tokenProvider(TokenProperties tokenProperties, Clock clock) {
    return switch (tokenProperties.impl()) {
      case NIMBUS -> new NimbusTokenProvider(tokenProperties, clock);
      case JJWT -> new JjwtTokenProvider(tokenProperties, clock);
    };
  }
}
