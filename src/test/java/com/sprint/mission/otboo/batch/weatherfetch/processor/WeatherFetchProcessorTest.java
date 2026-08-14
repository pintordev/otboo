package com.sprint.mission.otboo.batch.weatherfetch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class WeatherFetchProcessorTest {

  private static final BaseTime BASE_TIME = new BaseTime("20260727", "1700");
  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRefresher weatherRefresher;

  private WeatherFetchProcessor processor;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    // JobExecutionContext에서 SpEL로 주입받는 값을 생성자 인자로 직접 대신한다
    processor = new WeatherFetchProcessor(weatherRefresher, BASE_TIME.baseDate(),
        BASE_TIME.baseTime());
    logger = (Logger) LoggerFactory.getLogger(WeatherFetchProcessor.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  private WeatherGrid weatherGrid(int x, int y) {
    return FIXTURE_MONKEY.giveMeBuilder(WeatherGrid.class)
        .set("x", x)
        .set("y", y)
        .sample();
  }

  @Nested
  @DisplayName("Process")
  class Process {

    @Test
    @DisplayName("WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다")
    void WeatherGrid를_KmaGridPoint로_변환해서_WeatherRefresher에_위임한다() {
      // given
      WeatherGrid weatherGrid = weatherGrid(60, 127);
      List<Weather> saved = List.of();
      given(weatherRefresher.buildSlots(eq(weatherGrid), eq(new KmaGridPoint(60, 127)),
          eq(BASE_TIME))).willReturn(saved);

      // when
      List<Weather> result = processor.process(weatherGrid);

      // then
      assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("여러_격자를_처리해도_JobExecutionContext에서_주입받은_동일한_baseTime을_사용한다")
    void 여러_격자를_처리해도_JobExecutionContext에서_주입받은_동일한_baseTime을_사용한다() {
      // given
      WeatherGrid grid1 = weatherGrid(60, 127);
      WeatherGrid grid2 = weatherGrid(61, 128);

      // when
      processor.process(grid1);
      processor.process(grid2);

      // then
      ArgumentCaptor<BaseTime> baseTimeCaptor = ArgumentCaptor.forClass(BaseTime.class);
      verify(weatherRefresher, times(2)).buildSlots(any(), any(), baseTimeCaptor.capture());
      List<BaseTime> capturedBaseTimes = baseTimeCaptor.getAllValues();
      assertThat(capturedBaseTimes.get(0)).isEqualTo(BASE_TIME);
      assertThat(capturedBaseTimes.get(1)).isEqualTo(BASE_TIME);
    }
  }

  @Nested
  @DisplayName("KMA 호출 횟수 계측")
  class KmaCallCountLogging {

    @Test
    @DisplayName("격자를_처리할_때마다_누적_KMA_호출_횟수를_로그로_남긴다")
    void 격자를_처리할_때마다_누적_KMA_호출_횟수를_로그로_남긴다() {
      // given
      WeatherGrid grid1 = weatherGrid(60, 127);
      WeatherGrid grid2 = weatherGrid(61, 128);

      // when
      processor.process(grid1);
      processor.process(grid2);

      // then - skip 재실행으로 process()가 같은 chunk 안에서 여러 번 호출되면 그만큼 누적치가
      // 올라가는 걸 로그로 관측할 수 있어야 한다
      List<String> kmaCallLogs = appender.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .filter(message -> message.contains("누적 호출 횟수"))
          .toList();
      assertThat(kmaCallLogs)
          .anySatisfy(message -> assertThat(message).contains("누적 호출 횟수=1"))
          .anySatisfy(message -> assertThat(message).contains("누적 호출 횟수=2"));
    }

    @Test
    @DisplayName("호출_로그에_격자_좌표_등_위치_정보를_남기지_않는다")
    void 호출_로그에_격자_좌표_등_위치_정보를_남기지_않는다() {
      // given
      WeatherGrid grid = weatherGrid(60, 127);

      // when
      processor.process(grid);

      // then - conventions.md 11번: 위치 정보(위·경도 등)는 로그에 그대로 남기지 않는다
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(message -> message.contains("nx=") || message.contains("ny="));
    }
  }
}