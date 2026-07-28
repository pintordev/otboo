package com.sprint.mission.otboo.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("external")
@SpringBootTest
class KakaoLocalClientTest {

  @Autowired
  private KakaoLocalClient kakaoLocalClient;

  @Nested
  @DisplayName("GetRegionCode")
  class GetRegionCode {

    @Test
    @DisplayName("실제_카카오_API를_호출하면_행정동_정보를_받는다")
    void 실제_카카오_API를_호출하면_행정동_정보를_받는다() {
      // when
      KakaoRegionResponse response = kakaoLocalClient.getRegionCode(126.9884121, 37.5674783);

      // then
      assertThat(response.documents()).isNotEmpty();
      assertThat(response.documents())
          .anySatisfy(doc -> assertThat(doc.regionType()).isEqualTo("H"));
    }
  }
}