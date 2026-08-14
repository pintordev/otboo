package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Header;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Response;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KmaForecastFetcherTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Mock
  private KmaWeatherClient kmaWeatherClient;
  @Mock
  private KmaForecastParser kmaForecastParser;

  @Nested
  @DisplayName("FetchSlots")
  class FetchSlots {

    @Test
    @DisplayName("기상청_호출_후_날짜_슬롯순으로_정렬된_슬롯_예보를_반환한다")
    void 기상청_호출_후_날짜_슬롯순으로_정렬된_슬롯_예보를_반환한다() {
      // given
      KmaForecastFetcher fetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "kma-service-key");
      KmaGridPoint grid = FIXTURE_MONKEY.giveMeBuilder(KmaGridPoint.class)
          .set("nx", 60)
          .set("ny", 127)
          .sample();
      BaseTime baseTime = FIXTURE_MONKEY.giveMeBuilder(BaseTime.class)
          .set("baseDate", "20260727")
          .set("baseTime", "1700")
          .sample();

      KmaWeatherResponse response = new KmaWeatherResponse(
          new Response(new Header("00", "정상"), null));
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(response);

      WeatherForecastSlotDto late = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T09:00:00Z"))
          .sample();
      WeatherForecastSlotDto early = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .sample();
      given(kmaForecastParser.parseSlotForecast(response)).willReturn(List.of(late, early));

      // when
      List<WeatherForecastSlotDto> result = fetcher.fetchSlots(grid, baseTime);

      // then
      assertThat(result).containsExactly(early, late);
    }

    @Test
    @DisplayName("resultCode가_실패면_KmaApiException을_던진다")
    void resultCode가_실패면_KmaApiException을_던진다() {
      // given
      KmaForecastFetcher fetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "kma-service-key");
      KmaGridPoint grid = FIXTURE_MONKEY.giveMeBuilder(KmaGridPoint.class)
          .set("nx", 60)
          .set("ny", 127)
          .sample();
      BaseTime baseTime = FIXTURE_MONKEY.giveMeBuilder(BaseTime.class)
          .set("baseDate", "20260727")
          .set("baseTime", "1700")
          .sample();

      KmaWeatherResponse response = new KmaWeatherResponse(
          new Response(new Header("03", "NO_DATA"), null));
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(response);

      // when & then
      assertThatThrownBy(() -> fetcher.fetchSlots(grid, baseTime))
          .isInstanceOf(KmaApiException.class);
      verifyNoInteractions(kmaForecastParser);
    }

    @Test
    @DisplayName("클라이언트가_FeignException을_던지면_KmaApiException으로_wrap한다")
    void 클라이언트가_FeignException을_던지면_KmaApiException으로_wrap한다() {
      // given
      KmaForecastFetcher fetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "kma-service-key");
      KmaGridPoint grid = FIXTURE_MONKEY.giveMeBuilder(KmaGridPoint.class)
          .set("nx", 60)
          .set("ny", 127)
          .sample();
      BaseTime baseTime = FIXTURE_MONKEY.giveMeBuilder(BaseTime.class)
          .set("baseDate", "20260727")
          .set("baseTime", "1700")
          .sample();

      FeignException feignException = FeignException.errorStatus(
          "KmaWeatherClient#getVillageForecast", response("/getVillageForecast"));
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willThrow(feignException);

      // when & then
      assertThatThrownBy(() -> fetcher.fetchSlots(grid, baseTime))
          .isInstanceOf(KmaApiException.class);
      verifyNoInteractions(kmaForecastParser);
    }

    @Test
    @DisplayName("FeignException_메시지의_serviceKey를_마스킹한_뒤_cause로_보관한다")
    void FeignException_메시지의_serviceKey를_마스킹한_뒤_cause로_보관한다() {
      // given
      KmaForecastFetcher fetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "real-secret-key");
      KmaGridPoint grid = FIXTURE_MONKEY.giveMeBuilder(KmaGridPoint.class)
          .set("nx", 60)
          .set("ny", 127)
          .sample();
      BaseTime baseTime = FIXTURE_MONKEY.giveMeBuilder(BaseTime.class)
          .set("baseDate", "20260727")
          .set("baseTime", "1700")
          .sample();

      FeignException feignException = FeignException.errorStatus(
          "KmaWeatherClient#getVillageForecast",
          response("http://apis.data.go.kr/getVilageFcst?serviceKey=real-secret-key&pageNo=1"));
      given(kmaWeatherClient.getVillageForecast("real-secret-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willThrow(feignException);

      // when
      KmaApiException thrown = (KmaApiException) catchThrowable(
          () -> fetcher.fetchSlots(grid, baseTime));

      // then
      assertThat(thrown.getCause()).isNotNull();
      assertThat(thrown.getCause().getMessage()).contains("serviceKey=***");
      for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
        assertThat(cause.getMessage()).doesNotContain("real-secret-key");
      }
      verifyNoInteractions(kmaForecastParser);
    }

    private Request request() {
      return request("/getVillageForecast");
    }

    private Request request(String url) {
      return Request.create(Request.HttpMethod.GET, url, Map.of(),
          Request.Body.empty(), new RequestTemplate());
    }

    private feign.Response response(String url) {
      return feign.Response.builder()
          .request(request(url))
          .status(503)
          .reason("Service Unavailable")
          .headers(Map.of())
          .build();
    }
  }
}