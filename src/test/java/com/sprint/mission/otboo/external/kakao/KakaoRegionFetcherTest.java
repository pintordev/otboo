package com.sprint.mission.otboo.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoRegionFetcherTest {

  @Mock
  private KakaoLocalClient kakaoLocalClient;
  @Mock
  private KakaoRegionParser kakaoRegionParser;

  private KakaoRegionFetcher kakaoRegionFetcher;

  @Nested
  @DisplayName("Fetch")
  class Fetch {

    @Test
    @DisplayName("카카오_호출_후_행정구역명_목록을_반환한다")
    void 카카오_호출_후_행정구역명_목록을_반환한다() {
      // given
      kakaoRegionFetcher = new KakaoRegionFetcher(kakaoLocalClient, kakaoRegionParser);
      double latitude = 37.5674783;
      double longitude = 126.9884121;

      KakaoRegionResponse response = new KakaoRegionResponse(List.of());
      given(kakaoLocalClient.getRegionCode(longitude, latitude))
          .willReturn(response);
      given(kakaoRegionParser.toLocationNames(response, latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동", ""));

      // when
      List<String> result = kakaoRegionFetcher.fetch(latitude, longitude);

      // then
      assertThat(result).containsExactly("서울특별시", "중구", "명동", "");
    }
  }
}