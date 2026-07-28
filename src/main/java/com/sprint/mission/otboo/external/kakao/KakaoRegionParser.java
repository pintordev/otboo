package com.sprint.mission.otboo.external.kakao;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse.Document;
import com.sprint.mission.otboo.external.kakao.exception.LocationNotFoundException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KakaoRegionParser {

  private static final String ADMINISTRATIVE_DONG_TYPE = "H";

  public List<String> toLocationNames(KakaoRegionResponse response, double latitude,
      double longitude) {
    List<Document> documents = response.documents() != null ? response.documents() : List.of();
    return documents.stream()
        .filter(document -> ADMINISTRATIVE_DONG_TYPE.equals(document.regionType()))
        .findFirst()
        .map(document -> List.of(
            document.region1depthName(),
            document.region2depthName(),
            document.region3depthName(),
            document.region4depthName()
        ))
        .orElseThrow(() -> LocationNotFoundException.of(latitude, longitude));
  }
}