package com.sprint.mission.otboo.external.kakao;

import feign.Request;
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

  // Feign 기본 타임아웃(connect 10s, read 60s)은 SingleFlightRegistry의 lock TTL보다 훨씬
  // 길다 - 이 호출을 포함한 work가 락 만료 전에 반드시 끝나도록 KakaoFeignProperties로 줄인다.
  // SingleFlightLeaseTimeoutValidator가 기동 시점에 lock TTL보다 충분히 짧은지 검증한다.
  @Bean
  public Request.Options kakaoFeignOptions(KakaoFeignProperties kakaoFeignProperties) {
    return new Request.Options(kakaoFeignProperties.connect(), kakaoFeignProperties.read(), true);
  }
}