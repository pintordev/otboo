package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import feign.Logger;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaFeignConfigTest {

  private final KmaFeignConfig kmaFeignConfig = new KmaFeignConfig();

  @Nested
  @DisplayName("KmaFeignLoggerLevel")
  class KmaFeignLoggerLevel {

    @Test
    @DisplayName("로거_레벨은_NONE으로_고정된다")
    void 로거_레벨은_NONE으로_고정된다() {
      // when & then
      assertThat(kmaFeignConfig.kmaFeignLoggerLevel()).isEqualTo(Logger.Level.NONE);
    }
  }

  @Nested
  @DisplayName("KmaErrorDecoder")
  class KmaErrorDecoderTest {

    @Test
    @DisplayName("요청_URL의_serviceKey를_마스킹한_뒤_KmaApiException으로_wrap한다")
    void 요청_URL의_serviceKey를_마스킹한_뒤_KmaApiException으로_wrap한다() {
      // given
      String url = "http://apis.data.go.kr/getVilageFcst?serviceKey=real-secret-key&pageNo=1";
      Request request = Request.create(Request.HttpMethod.GET, url, Map.of(),
          Request.Body.empty(), new RequestTemplate());
      Response response = Response.builder()
          .request(request)
          .status(500)
          .reason("Internal Server Error")
          .headers(Map.of())
          .build();
      ErrorDecoder errorDecoder = kmaFeignConfig.kmaErrorDecoder();

      // when
      Exception exception = errorDecoder.decode("KmaWeatherClient#getVillageForecast", response);

      // then
      assertThat(exception).isInstanceOf(KmaApiException.class);
      assertThat(exception.getCause()).isNotNull();
      assertThat(exception.getCause().getMessage())
          .doesNotContain("real-secret-key")
          .contains("serviceKey=***");
    }
  }
}