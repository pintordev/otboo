package com.sprint.mission.otboo.global.config;

import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.global.interceptor.MdcLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final MdcLoggingInterceptor mdcLoggingInterceptor;

  @Value("${otboo.cors.allowed-origins}")
  private String[] allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(mdcLoggingInterceptor)
        .addPathPatterns("/**");
  }

  // 정렬 기준 enum이 생기면 addFormatters()에 Converter<String, Enum> 등록
  // 예시: docs/guide/querydsl.md 5번 참고
  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, FeedSortBy.class, value -> switch (value) {
      case "createdAt" -> FeedSortBy.CREATED_AT;
      case "likeCount" -> FeedSortBy.LIKE_COUNT;
      default -> FeedSortBy.CREATED_AT;
    });
    // 정렬 기준 enum이 있는 도메인마다 추가
  }
}