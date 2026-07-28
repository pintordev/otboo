package com.sprint.mission.otboo.external.kakao;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class KakaoRegionFetcher {

  private final KakaoLocalClient kakaoLocalClient;
  private final KakaoRegionParser kakaoRegionParser;

  public List<String> fetch(double latitude, double longitude) {
    KakaoRegionResponse response = kakaoLocalClient.getRegionCode(longitude, latitude);
    return kakaoRegionParser.toLocationNames(response, latitude, longitude);
  }
}