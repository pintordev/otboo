package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Body;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Header;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Items;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Response;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class KmaForecastParserTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  private final KmaForecastParser parser = new KmaForecastParser();

  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUpLogger() {
    logger = (Logger) LoggerFactory.getLogger(KmaForecastParser.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogger() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Nested
  @DisplayName("ParseSlotForecast")
  class ParseSlotForecast {

    @Test
    @DisplayName("근접일_1시간_간격_24개_슬롯_응답이_슬롯마다_1개씩_24개_DTO로_분해된다")
    void 근접일_1시간_간격_24개_슬롯_응답이_슬롯마다_1개씩_24개_DTO로_분해된다() {
      // given - 0시~23시 매시 TMP, 0시 슬롯에만 다른 카테고리도 함께 포함
      List<Item> items = new ArrayList<>();
      for (int hour = 0; hour < 24; hour++) {
        items.add(item("TMP", "20260729", "%02d00".formatted(hour), String.valueOf(15 + hour)));
      }
      items.add(item("SKY", "20260729", "0000", "1"));
      items.add(item("PTY", "20260729", "0000", "1"));
      items.add(item("POP", "20260729", "0000", "60"));
      items.add(item("PCP", "20260729", "0000", "5.0mm"));
      items.add(item("REH", "20260729", "0000", "70"));
      items.add(item("WSD", "20260729", "0000", "4.0"));
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).hasSize(24);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMin).containsOnly(15.0);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMax).containsOnly(38.0);

      ZoneId kst = ZoneId.of("Asia/Seoul");
      LocalDate date = LocalDate.of(2026, 7, 29);
      WeatherForecastSlotDto hour0 = result.stream()
          .filter(dto -> dto.slotAt().equals(date.atTime(0, 0).atZone(kst).toInstant()))
          .findFirst().orElseThrow();
      assertThat(hour0.temperatureCurrent()).isEqualTo(15.0);
      assertThat(hour0.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(hour0.precipitationType()).isEqualTo(PrecipitationType.RAIN);
      assertThat(hour0.precipitationProbability()).isEqualTo(60.0);
      assertThat(hour0.precipitationAmount()).isEqualTo(5.0);
      assertThat(hour0.humidityCurrent()).isEqualTo(70.0);
      assertThat(hour0.windSpeed()).isEqualTo(4.0);

      WeatherForecastSlotDto hour1 = result.stream()
          .filter(dto -> dto.slotAt().equals(date.atTime(1, 0).atZone(kst).toInstant()))
          .findFirst().orElseThrow();
      assertThat(hour1.temperatureCurrent()).isEqualTo(16.0);
      assertThat(hour1.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(hour1.precipitationType()).isEqualTo(PrecipitationType.NONE);
      // POP/PCP는 하루 대표값이라 PTY와 달리 이 슬롯에도 0시 슬롯과 동일한 값이 적용된다
      assertThat(hour1.precipitationProbability()).isEqualTo(60.0);
      assertThat(hour1.precipitationAmount()).isEqualTo(5.0);
      assertThat(hour1.humidityCurrent()).isEqualTo(0.0);
      assertThat(hour1.windSpeed()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("근접일 이후는_3시간_간격_8개_슬롯_응답이_슬롯마다_1개씩_8개_DTO로_분해된다")
    void 근접일_이후는_3시간_간격_8개_슬롯_응답이_슬롯마다_1개씩_8개_DTO로_분해된다() {
      // given - 0시부터 3시간 간격 8슬롯(근접일 이후 그리드)
      List<Item> items = new ArrayList<>();
      String[] slotTimes = {"0000", "0300", "0600", "0900", "1200", "1500", "1800", "2100"};
      double[] temps = {18, 17, 19, 23, 27, 28, 25, 21};
      for (int i = 0; i < slotTimes.length; i++) {
        items.add(item("TMP", "20260801", slotTimes[i], String.valueOf(temps[i])));
      }
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).hasSize(8);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMin).containsOnly(17.0);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMax).containsOnly(28.0);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureCurrent)
          .containsExactlyInAnyOrder(18.0, 17.0, 19.0, 23.0, 27.0, 28.0, 25.0, 21.0);
    }

    @Test
    @DisplayName("TMP가_없는_슬롯은_다른_슬롯이_충분해도_결과에서_제외된다")
    void TMP가_없는_슬롯은_다른_슬롯이_충분해도_결과에서_제외된다() {
      // given - TMP는 4개 슬롯뿐이라 날짜 게이트는 통과하지만, 0600엔 SKY만 있고 TMP가 없다
      List<Item> items = List.of(
          item("TMP", "20260802", "0000", "18"),
          item("TMP", "20260802", "0300", "17"),
          item("TMP", "20260802", "0900", "23"),
          item("TMP", "20260802", "1200", "27"),
          item("SKY", "20260802", "0600", "1")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).hasSize(4);
      assertThat(result).extracting(WeatherForecastSlotDto::slotAt)
          .noneMatch(slotAt -> slotAt.equals(
              LocalDate.of(2026, 8, 2).atTime(6, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()));
    }

    @Test
    @DisplayName("TMN_TMX가_있으면_TMP_극값_대신_그_값을_일_기온_범위로_쓴다")
    void TMN_TMX가_있으면_TMP_극값_대신_그_값을_일_기온_범위로_쓴다() {
      // given - TMP 극값(17.0~19.0)과 다른 TMN(10.0)/TMX(25.0)
      List<Item> items = List.of(
          item("TMP", "20260803", "0000", "17.0"),
          item("TMP", "20260803", "0300", "18.0"),
          item("TMP", "20260803", "0600", "19.0"),
          item("TMP", "20260803", "0900", "18.5"),
          item("TMN", "20260803", "0600", "10.0"),
          item("TMX", "20260803", "1500", "25.0")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMin).containsOnly(10.0);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMax).containsOnly(25.0);
    }

    @Test
    @DisplayName("TMN_TMX가_없으면_TMP_극값으로_대체한다")
    void TMN_TMX가_없으면_TMP_극값으로_대체한다() {
      // given
      List<Item> items = List.of(
          item("TMP", "20260803", "0000", "17.0"),
          item("TMP", "20260803", "0300", "18.0"),
          item("TMP", "20260803", "0600", "19.0"),
          item("TMP", "20260803", "0900", "18.5")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMin).containsOnly(17.0);
      assertThat(result).extracting(WeatherForecastSlotDto::temperatureMax).containsOnly(19.0);
    }

    @Test
    @DisplayName("SKY_미상_코드는_CLEAR로_대체하고_경고_로그를_남긴다")
    void SKY_미상_코드는_CLEAR로_대체하고_경고_로그를_남긴다() {
      // given
      List<Item> items = List.of(
          item("TMP", "20260803", "0000", "17.0"),
          item("TMP", "20260803", "0300", "18.0"),
          item("TMP", "20260803", "0600", "19.0"),
          item("TMP", "20260803", "0900", "18.5"),
          item("SKY", "20260803", "0000", "9")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      Instant slotAt0000 = LocalDate.of(2026, 8, 3).atStartOfDay(ZoneId.of("Asia/Seoul"))
          .toInstant();
      WeatherForecastSlotDto slot0000 = result.stream()
          .filter(dto -> dto.slotAt().equals(slotAt0000)).findFirst().orElseThrow();
      assertThat(slot0000.skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(appender.list)
          .anySatisfy(logEvent -> {
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("SKY").contains("9");
          });
    }

    @Test
    @DisplayName("PTY_미상_코드는_NONE으로_대체하고_경고_로그를_남긴다")
    void PTY_미상_코드는_NONE으로_대체하고_경고_로그를_남긴다() {
      // given
      List<Item> items = List.of(
          item("TMP", "20260803", "0000", "17.0"),
          item("TMP", "20260803", "0300", "18.0"),
          item("TMP", "20260803", "0600", "19.0"),
          item("TMP", "20260803", "0900", "18.5"),
          item("PTY", "20260803", "0000", "9")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      Instant slotAt0000 = LocalDate.of(2026, 8, 3).atStartOfDay(ZoneId.of("Asia/Seoul"))
          .toInstant();
      WeatherForecastSlotDto slot0000 = result.stream()
          .filter(dto -> dto.slotAt().equals(slotAt0000)).findFirst().orElseThrow();
      assertThat(slot0000.precipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(appender.list)
          .anySatisfy(logEvent -> {
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("PTY").contains("9");
          });
    }

    @Test
    @DisplayName("PTY_값이_null이면_강수형태를_NONE으로_두고_NPE_없이_파싱한다")
    void PTY_값이_null이면_강수형태를_NONE으로_두고_NPE_없이_파싱한다() {
      // given - 기상청 응답에서 PTY 항목의 fcstValue가 null인 경우(결측)
      List<Item> items = new ArrayList<>();
      for (int hour = 0; hour < 4; hour++) {
        items.add(item("TMP", "20260824", "%02d00".formatted(hour), String.valueOf(20 + hour)));
      }
      items.add(item("PTY", "20260824", "0000", null));
      KmaWeatherResponse response = responseOf(items);

      // when / then
      assertThatCode(() -> parser.parseSlotForecast(response)).doesNotThrowAnyException();
      WeatherForecastSlotDto hour0 = parser.parseSlotForecast(response).stream()
          .filter(dto -> dto.slotAt().equals(
              LocalDate.of(2026, 8, 24).atTime(0, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant()))
          .findFirst().orElseThrow();
      assertThat(hour0.precipitationType()).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    @DisplayName("POP는_하루_최댓값_PCP는_하루_합계로_모든_슬롯에_동일하게_적용된다")
    void POP는_하루_최댓값_PCP는_하루_합계로_모든_슬롯에_동일하게_적용된다() {
      // given - 06시 POP 80/PCP 20mm, 15시 POP 10/PCP 0mm인 날(문서 2번 시나리오)
      List<Item> items = new ArrayList<>();
      items.add(item("TMP", "20260824", "0600", "20"));
      items.add(item("POP", "20260824", "0600", "80"));
      items.add(item("PCP", "20260824", "0600", "20.0mm"));
      items.add(item("TMP", "20260824", "1500", "28"));
      items.add(item("POP", "20260824", "1500", "10"));
      items.add(item("PCP", "20260824", "1500", "강수없음"));
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then - 15시 슬롯의 원값(POP 10/PCP 0)이 아니라 하루 대표값(80/20.0)이 모든 슬롯에 똑같이 적용된다
      assertThat(result).extracting(WeatherForecastSlotDto::precipitationProbability)
          .containsOnly(80.0);
      assertThat(result).extracting(WeatherForecastSlotDto::precipitationAmount)
          .containsOnly(20.0);
    }

    @Test
    @DisplayName("당일_늦은_시각_base_time으로_남은_슬롯이_3개뿐이어도_버려지지_않는다")
    void 당일_늦은_시각_base_time으로_남은_슬롯이_3개뿐이어도_버려지지_않는다() {
      // given - 20시 base_time 조회 시 오늘 남은 슬롯은 21·22·23시 3개뿐(기존 MIN_SLOT_COUNT=4 미달)
      List<Item> items = new ArrayList<>();
      for (int hour = 21; hour <= 23; hour++) {
        items.add(item("TMP", "20260824", "%02d00".formatted(hour), String.valueOf(20 + hour)));
      }
      KmaWeatherResponse response = responseOf(items);

      // when
      List<WeatherForecastSlotDto> result = parser.parseSlotForecast(response);

      // then
      assertThat(result).hasSize(3);
    }
  }

  private Item item(String category, String fcstDate, String fcstTime, String fcstValue) {
    return FIXTURE_MONKEY.giveMeBuilder(Item.class)
        .set("baseDate", "20260727")
        .set("baseTime", "1700")
        .set("category", category)
        .set("fcstDate", fcstDate)
        .set("fcstTime", fcstTime)
        .set("fcstValue", fcstValue)
        .set("nx", 60)
        .set("ny", 127)
        .sample();
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