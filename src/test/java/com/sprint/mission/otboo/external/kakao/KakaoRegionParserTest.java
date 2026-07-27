package com.sprint.mission.otboo.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse.Document;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KakaoRegionParserTest {

  private final KakaoRegionParser parser = new KakaoRegionParser();

  @Nested
  @DisplayName("ToLocationNames")
  class ToLocationNames {

    @Test
    @DisplayName("행정동_문서에서_지역명_4단계를_추출한다")
    void 행정동_문서에서_지역명_4단계를_추출한다() {
      // given
      KakaoRegionResponse response = new KakaoRegionResponse(List.of(
          new Document("B", "서울특별시 중구 명동", "서울특별시", "중구", "명동", ""),
          new Document("H", "서울특별시 중구 명동", "서울특별시", "중구", "명동", "")
      ));

      // when
      List<String> locationNames = parser.toLocationNames(response);

      // then
      assertThat(locationNames).containsExactly("서울특별시", "중구", "명동", "");
    }
  }
}