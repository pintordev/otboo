package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Body;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Header;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Items;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KmaForecastParserTest {

  private final KmaForecastParser parser = new KmaForecastParser();

  @Nested
  @DisplayName("ParseDailyForecast")
  class ParseDailyForecast {

    @Test
    @DisplayName("미래_날짜의_하루치_데이터가_주어지면_대표시각_1500_기준으로_집계된다")
    void 미래_날짜의_하루치_데이터가_주어지면_대표시각_1500_기준으로_집계된다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260729", "0000", "24"),
          item("TMP", "20260729", "0300", "23"),
          item("TMP", "20260729", "0600", "24"),
          item("TMP", "20260729", "0900", "27"),
          item("TMP", "20260729", "1200", "30"),
          item("TMP", "20260729", "1500", "32"),
          item("TMP", "20260729", "1800", "29"),
          item("TMP", "20260729", "2100", "26"),
          item("SKY", "20260729", "1500", "1"),
          item("PTY", "20260729", "0000", "0"),
          item("PTY", "20260729", "1500", "0"),
          item("POP", "20260729", "0000", "10"),
          item("POP", "20260729", "0900", "30"),
          item("POP", "20260729", "1500", "10"),
          item("PCP", "20260729", "0000", "강수없음"),
          item("PCP", "20260729", "1500", "강수없음"),
          item("REH", "20260729", "1500", "55"),
          item("WSD", "20260729", "1500", "3.0")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      DailyWeatherForecastDto dto = result.get(0);
      assertThat(dto.date()).isEqualTo(LocalDate.of(2026, 7, 29));
      assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(dto.precipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(dto.precipitationAmount()).isEqualTo(0.0);
      assertThat(dto.precipitationProbability()).isEqualTo(30.0);
      assertThat(dto.humidityCurrent()).isEqualTo(55.0);
      assertThat(dto.temperatureCurrent()).isEqualTo(32.0);
      assertThat(dto.temperatureMin()).isEqualTo(23.0);
      assertThat(dto.temperatureMax()).isEqualTo(32.0);
      assertThat(dto.windSpeed()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("오늘_날짜는_지금_시각과_가장_가까운_슬롯_값을_current로_사용한다")
    void 오늘_날짜는_지금_시각과_가장_가까운_슬롯_값을_current로_사용한다() {
      // given - now: 2026-07-27 20:00 KST
      Instant now = Instant.parse("2026-07-27T11:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260727", "1800", "31"),
          item("TMP", "20260727", "2000", "28"),
          item("TMP", "20260727", "2100", "27")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).temperatureCurrent()).isEqualTo(28.0);
    }
  }

  private Item item(String category, String fcstDate, String fcstTime, String fcstValue) {
    return new Item("20260727", "1700", category, fcstDate, fcstTime, fcstValue, 60, 127);
  }

  private KmaWeatherResponse responseOf(List<Item> items) {
    return new KmaWeatherResponse(
        new Response(
            new Header("00", "NORMAL_SERVICE"),
            new Body("JSON", new Items(items), 1, 1000, items.size())
        )
    );
  }
}