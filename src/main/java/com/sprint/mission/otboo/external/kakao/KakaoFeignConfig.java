package com.sprint.mission.otboo.external.kakao;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// @FeignClient(configuration = ...)로만 로드되는 kakaoLocalClient 전용 설정.
// @Configuration을 붙이지 않아 컴포넌트 스캔으로 전역에 걸리지 않는다 — 이 인터셉터는
// kakaoLocalClient에만 적용되고 다른 Feign 클라이언트(KmaWeatherClient)에는 영향 없음.
public class KakaoFeignConfig {

  @Bean
  public RequestInterceptor kakaoAuthInterceptor(
      @Value("${weather.kakao.rest-api-key}") String kakaoRestApiKey) {
    return requestTemplate -> requestTemplate.header("Authorization",
        "KakaoAK " + kakaoRestApiKey);
  }
}