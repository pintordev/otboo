package com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums;

import lombok.Getter;

@Getter
public enum PrecipitationType {
  NONE("없음"),
  RAIN("비"),
  RAIN_SNOW("비/눈"),
  SNOW("눈"),
  SHOWER("소나기");

  // 알림 본문 등 사용자 노출용 표시명 - api-docs.json 계약상 API 응답 직렬화는 name()을
  // 그대로 쓰므로(EnumType.STRING/Jackson 기본 직렬화) 이 필드엔 @JsonValue를 붙이지 않는다
  private final String label;

  PrecipitationType(String label) {
    this.label = label;
  }
}