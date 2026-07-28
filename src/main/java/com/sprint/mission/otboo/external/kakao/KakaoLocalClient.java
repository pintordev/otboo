package com.sprint.mission.otboo.external.kakao;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "kakaoLocalClient",
    url = "${weather.kakao.base-url}",
    configuration = KakaoFeignConfig.class
)
public interface KakaoLocalClient {

  @GetMapping
  KakaoRegionResponse getRegionCode(
      @RequestParam("x") double longitude,
      @RequestParam("y") double latitude
  );
}