package com.sprint.mission.otboo.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoRegionResponse(List<Document> documents) {

  public record Document(
      @JsonProperty("region_type") String regionType,
      @JsonProperty("address_name") String addressName,
      @JsonProperty("region_1depth_name") String region1depthName,
      @JsonProperty("region_2depth_name") String region2depthName,
      @JsonProperty("region_3depth_name") String region3depthName,
      @JsonProperty("region_4depth_name") String region4depthName
  ) {

  }
}