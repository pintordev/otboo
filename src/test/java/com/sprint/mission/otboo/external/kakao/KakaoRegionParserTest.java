package com.sprint.mission.otboo.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.external.kakao.exception.LocationNotFoundException;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse.Document;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KakaoRegionParserTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  private final KakaoRegionParser parser = new KakaoRegionParser();

  @Nested
  @DisplayName("ToLocationNames")
  class ToLocationNames {

    @Test
    @DisplayName("행정동_문서에서_지역명_4단계를_추출한다")
    void 행정동_문서에서_지역명_4단계를_추출한다() {
      // given
      Document legalDongDocument = document("B", "명동");
      Document administrativeDongDocument = document("H", "명동");
      KakaoRegionResponse response = new KakaoRegionResponse(
          List.of(legalDongDocument, administrativeDongDocument));

      // when
      List<String> locationNames = parser.toLocationNames(response, 37.5674783, 126.9884121);

      // then
      assertThat(locationNames).containsExactly("서울특별시", "중구", "명동", "");
    }

    @Test
    @DisplayName("행정동_문서가_없으면_LocationNotFoundException을_던진다")
    void 행정동_문서가_없으면_LocationNotFoundException을_던진다() {
      // given
      Document legalDongDocument = document("B", "명동");
      KakaoRegionResponse response = new KakaoRegionResponse(List.of(legalDongDocument));

      // when & then
      assertThatThrownBy(() -> parser.toLocationNames(response, 37.5674783, 126.9884121))
          .isInstanceOf(LocationNotFoundException.class);
    }
  }

  private Document document(String regionType, String region3depthName) {
    return FIXTURE_MONKEY.giveMeBuilder(Document.class)
        .set("regionType", regionType)
        .set("addressName", "서울특별시 중구 " + region3depthName)
        .set("region1depthName", "서울특별시")
        .set("region2depthName", "중구")
        .set("region3depthName", region3depthName)
        .set("region4depthName", "")
        .sample();
  }
}