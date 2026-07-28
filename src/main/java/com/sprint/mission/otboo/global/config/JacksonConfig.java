package com.sprint.mission.otboo.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  // 프로젝트는 Jackson 3(tools.jackson)로 자동 구성되어 있어 스프링이 자동으로 만들어주는
  // ObjectMapper는 tools.jackson.databind.ObjectMapper 타입이다. 네이티브 쿼리에 넘길
  // JSON 문자열을 만들 때 쓰는 클래식 Jackson(com.fasterxml.jackson)은 자동 구성 대상이
  // 아니라서 직접 빈으로 등록한다 (타입이 달라 자동 구성 빈과 충돌하지 않음).
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}