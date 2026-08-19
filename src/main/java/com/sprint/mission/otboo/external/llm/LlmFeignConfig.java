package com.sprint.mission.otboo.external.llm;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class LlmFeignConfig {

  @Bean
  public RequestInterceptor llmAuthInterceptor(
      @Value("${external.llm.api-key}") String apiKey) {
    return requestTemplate -> requestTemplate.header("Authorization",
        "Bearer " + apiKey);
  }
}
