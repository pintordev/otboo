package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// @FeignClient(configuration = ...)로만 로드되는 kmaWeatherClient 전용 설정.
// @Configuration을 붙이지 않아 컴포넌트 스캔으로 전역에 걸리지 않는다 — 이 로거 레벨/ErrorDecoder는
// kmaWeatherClient에만 적용되고 다른 Feign 클라이언트(KakaoLocalClient)에는 영향 없음.
public class KmaFeignConfig {

  @Bean
  public Logger.Level kmaFeignLoggerLevel() {
    return Logger.Level.NONE;
  }

  // Feign 기본 타임아웃(connect 10s, read 60s)은 SingleFlightRegistry의 lock TTL보다 훨씬
  // 길다 - 이 호출을 포함한 work가 락 만료 전에 반드시 끝나도록 KmaFeignProperties로 줄인다.
  // SingleFlightLeaseTimeoutValidator가 기동 시점에 lock TTL보다 충분히 짧은지 검증한다.
  @Bean
  public Request.Options kmaFeignOptions(KmaFeignProperties kmaFeignProperties) {
    return new Request.Options(kmaFeignProperties.connect(), kmaFeignProperties.read(), true);
  }

  @Bean
  public ErrorDecoder kmaErrorDecoder() {
    return (methodKey, response) -> {
      String maskedUrl = KmaServiceKeyMasker.mask(response.request().url());
      return KmaApiException.wrap(new RuntimeException(maskedUrl));
    };
  }
}