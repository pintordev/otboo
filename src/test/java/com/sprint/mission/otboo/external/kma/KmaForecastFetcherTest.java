package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Header;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Response;
import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  private KmaForecastFetcher kmaForecastFetcher;

  @Nested
  @DisplayName("Fetch")
  class Fetch {

    @Test
    @DisplayName("기상청_호출_후_날짜순으로_정렬된_예보를_반환한다")
    void 기상청_호출_후_날짜순으로_정렬된_예보를_반환한다() {
      // given
      kmaForecastFetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "kma-service-key");
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      BaseTime baseTime = new BaseTime("20260727", "1700");
      Instant now = Instant.parse("2026-07-27T09:00:00Z");

      KmaWeatherResponse response = new KmaWeatherResponse(
          new Response(new Header("00", "정상"), null));
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(response);

      DailyWeatherForecastDto dayTwo = FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 28))
          .sample();
      DailyWeatherForecastDto dayOne = FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .sample();
      given(kmaForecastParser.parseDailyForecast(eq(response), eq(now)))
          .willReturn(List.of(dayTwo, dayOne));

      // when
      List<DailyWeatherForecastDto> result = kmaForecastFetcher.fetch(grid, baseTime, now);

      // then
      assertThat(result).containsExactly(dayOne, dayTwo);
    }

    @Test
    @DisplayName("resultCode가_실패면_KmaApiException을_던진다")
    void resultCode가_실패면_KmaApiException을_던진다() {
      // given
      kmaForecastFetcher = new KmaForecastFetcher(kmaWeatherClient, kmaForecastParser,
          "kma-service-key");
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      BaseTime baseTime = new BaseTime("20260727", "1700");
      Instant now = Instant.parse("2026-07-27T09:00:00Z");

      KmaWeatherResponse response = new KmaWeatherResponse(
          new Response(new Header("03", "NO_DATA"), null));
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 2000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(response);

      // when & then
      assertThatThrownBy(() -> kmaForecastFetcher.fetch(grid, baseTime, now))
          .isInstanceOf(KmaApiException.class);
      verifyNoInteractions(kmaForecastParser);
    }
  }
}