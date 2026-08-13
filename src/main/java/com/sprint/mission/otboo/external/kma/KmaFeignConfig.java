package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import feign.Logger;
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

  @Bean
  public ErrorDecoder kmaErrorDecoder() {
    return (methodKey, response) -> {
      String maskedUrl = KmaServiceKeyMasker.mask(response.request().url());
      return KmaApiException.wrap(new RuntimeException(maskedUrl));
    };
  }
}