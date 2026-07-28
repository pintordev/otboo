package com.sprint.mission.otboo.external.kakao;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoRegionFetcher {

  private final KakaoLocalClient kakaoLocalClient;
  private final KakaoRegionParser kakaoRegionParser;
  private final String kakaoRestApiKey;

  public KakaoRegionFetcher(KakaoLocalClient kakaoLocalClient,
      KakaoRegionParser kakaoRegionParser,
      @Value("${weather.kakao.rest-api-key}") String kakaoRestApiKey) {
    this.kakaoLocalClient = kakaoLocalClient;
    this.kakaoRegionParser = kakaoRegionParser;
    this.kakaoRestApiKey = kakaoRestApiKey;
  }

  public List<String> fetch(double latitude, double longitude) {
    KakaoRegionResponse response = kakaoLocalClient.getRegionCode("KakaoAK " + kakaoRestApiKey,
        longitude, latitude);
    return kakaoRegionParser.toLocationNames(response);
  }
}