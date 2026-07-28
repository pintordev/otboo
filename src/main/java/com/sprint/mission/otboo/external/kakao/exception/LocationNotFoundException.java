package com.sprint.mission.otboo.external.kakao.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class LocationNotFoundException extends KakaoException {

  private static final String MESSAGE = "해당 좌표의 행정구역 정보를 찾을 수 없습니다.";

  private LocationNotFoundException(Map<String, Object> details) {
    super(HttpStatus.NOT_FOUND, MESSAGE, details);
  }

  public static LocationNotFoundException of(double latitude, double longitude) {
    return new LocationNotFoundException(Map.of("latitude", latitude, "longitude", longitude));
  }
}