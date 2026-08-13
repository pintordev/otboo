package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaServiceKeyMaskerTest {

  @Nested
  @DisplayName("Mask")
  class Mask {

    @Test
    @DisplayName("serviceKey_쿼리_파라미터_값을_마스킹한다")
    void serviceKey_쿼리_파라미터_값을_마스킹한다() {
      // given
      String url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
          + "?serviceKey=abcd1234secret&numOfRows=10&pageNo=1";

      // when
      String masked = KmaServiceKeyMasker.mask(url);

      // then
      assertThat(masked)
          .doesNotContain("abcd1234secret")
          .contains("serviceKey=***")
          .contains("numOfRows=10");
    }

    @Test
    @DisplayName("serviceKey가_없으면_원본_그대로_반환한다")
    void serviceKey가_없으면_원본_그대로_반환한다() {
      // given
      String text = "그냥 아무 메시지";

      // when
      String masked = KmaServiceKeyMasker.mask(text);

      // then
      assertThat(masked).isEqualTo(text);
    }

    @Test
    @DisplayName("null이면_null을_반환한다")
    void null이면_null을_반환한다() {
      // when & then
      assertThat(KmaServiceKeyMasker.mask(null)).isNull();
    }
  }
}