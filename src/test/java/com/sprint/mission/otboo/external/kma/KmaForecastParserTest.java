package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
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
          item("TMP", "20260727", "1900", "30"),
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

    @Test
    @DisplayName("PCP_텍스트_표현을_파싱해서_하루_합계로_집계한다")
    void PCP_텍스트_표현을_파싱해서_하루_합계로_집계한다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260730", "1500", "28"),
          item("PCP", "20260730", "0600", "1.0mm"),
          item("PCP", "20260730", "0900", "강수없음"),
          item("PCP", "20260730", "1200", "1mm 미만"),
          item("PCP", "20260730", "1500", "30.0~50.0mm")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).precipitationAmount()).isEqualTo(31.9);
    }

    @Test
    @DisplayName("대표시각에_강수가_없어도_다른_시각에_강수가_있으면_그_타입을_채택한다")
    void 대표시각에_강수가_없어도_다른_시각에_강수가_있으면_그_타입을_채택한다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260730", "1500", "28"),
          item("PTY", "20260730", "0000", "0"),
          item("PTY", "20260730", "0900", "1"),
          item("PTY", "20260730", "1200", "0"),
          item("PTY", "20260730", "1500", "0")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).precipitationType()).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    @DisplayName("슬롯이_1개뿐인_응답_창_끝단_날짜는_결과에서_제외된다")
    void 슬롯이_1개뿐인_응답_창_끝단_날짜는_결과에서_제외된다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260729", "0000", "24"),
          item("TMP", "20260729", "0300", "23"),
          item("TMP", "20260729", "0600", "24"),
          item("TMP", "20260729", "0900", "27"),
          // 응답 창 끝단 - 슬롯 1개뿐인 부실한 날짜
          item("TMP", "20260801", "0000", "26")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 29));
    }

    @Test
    @DisplayName("TMP_데이터가_없는_날짜는_슬롯_수가_충분해도_결과에서_제외된다")
    void TMP_데이터가_없는_날짜는_슬롯_수가_충분해도_결과에서_제외된다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("PCP", "20260730", "0000", "강수없음"),
          item("PCP", "20260730", "0900", "강수없음"),
          item("PCP", "20260730", "1200", "강수없음"),
          item("PCP", "20260730", "1500", "강수없음")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SKY_미상_코드는_CLEAR로_대체하고_경고_로그를_남긴다")
    void SKY_미상_코드는_CLEAR로_대체하고_경고_로그를_남긴다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260730", "0000", "24"),
          item("TMP", "20260730", "0900", "27"),
          item("TMP", "20260730", "1200", "30"),
          item("TMP", "20260730", "1500", "28"),
          item("SKY", "20260730", "1500", "9")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
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
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260730", "0000", "24"),
          item("TMP", "20260730", "0900", "27"),
          item("TMP", "20260730", "1200", "30"),
          item("TMP", "20260730", "1500", "28"),
          item("PTY", "20260730", "1500", "9")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).precipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(appender.list)
          .anySatisfy(logEvent -> {
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("PTY").contains("9");
          });
    }

    @Test
    @DisplayName("유효_PTY가_먼저_우선순위를_차지해도_그_뒤_미상_PTY는_경고_로그를_남긴다")
    void 유효_PTY가_먼저_우선순위를_차지해도_그_뒤_미상_PTY는_경고_로그를_남긴다() {
      // given
      Instant now = Instant.parse("2026-07-27T08:00:00Z");
      List<Item> items = List.of(
          item("TMP", "20260730", "0000", "24"),
          item("TMP", "20260730", "0900", "27"),
          item("TMP", "20260730", "1200", "30"),
          item("TMP", "20260730", "1500", "28"),
          item("PTY", "20260730", "1500", "1"),
          item("PTY", "20260730", "0900", "9")
      );
      KmaWeatherResponse response = responseOf(items);

      // when
      List<DailyWeatherForecastDto> result = parser.parseDailyForecast(response, now);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).precipitationType()).isEqualTo(PrecipitationType.RAIN);
      assertThat(appender.list)
          .anySatisfy(logEvent -> {
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("PTY").contains("9");
          });
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