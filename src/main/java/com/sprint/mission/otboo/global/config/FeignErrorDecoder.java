package com.sprint.mission.otboo.global.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

  private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

  @Override
  public Exception decode(String methodKey, Response response) {
    log.error("외부 API 호출 실패: method={}, status={}", methodKey, response.status());
    return defaultDecoder.decode(methodKey, response);
  }
}