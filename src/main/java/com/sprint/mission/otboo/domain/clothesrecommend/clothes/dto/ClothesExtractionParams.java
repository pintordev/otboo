package com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Set;
import org.hibernate.validator.constraints.URL;

public record ClothesExtractionParams(
    @NotBlank @URL String url
) {

  private static final Set<String> ALLOWED_HOSTS = Set.of(
      "www.musinsa.com", "musinsa.com", "store.musinsa.com",
      "www.29cm.co.kr", "29cm.co.kr",
      "www.wconcept.co.kr", "wconcept.co.kr",
      "zigzag.kr", "www.zigzag.kr"
  );

  @AssertTrue(message = "유효하지 않은 URL입니다.")
  public boolean isAllowedHost() {
    if (url == null || url.isBlank()) {
      return true; // @NotBlank가 별도로 처리
    }
    try {
      URI uri = URI.create(url);
      return "https".equals(uri.getScheme())
          && uri.getHost() != null
          && ALLOWED_HOSTS.contains(uri.getHost());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
