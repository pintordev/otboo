package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WeatherWriterUpsertGuardTest extends IntegrationTestSupport {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Autowired
  private WeatherWriter weatherWriter;
  @Autowired
  private WeatherRepository weatherRepository;
  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @BeforeEach
  void setUp() {
    cleanUpWeatherTables();
  }

  @AfterEach
  void tearDown() {
    // @SpringBootTest는 @DataJpaTest와 달리 트랜잭션이 자동 롤백되지 않고 실제 커밋되므로,
    // 같은 Testcontainers DB를 공유하는 다른 테스트 클래스가 이 테스트의 잔여 데이터와
    // 충돌하지 않도록 종료 시점에도 정리한다
    cleanUpWeatherTables();
  }

  private void cleanUpWeatherTables() {
    weatherRepository.deleteAll();
    weatherGridRepository.deleteAll();
  }

  @Test
  @DisplayName("늦게_도착한_이전_forecastedAt_upsert는_이미_반영된_최신_forecastedAt을_덮어쓰지_않는다")
  void 늦게_도착한_이전_forecastedAt_upsert는_이미_반영된_최신_forecastedAt을_덮어쓰지_않는다() {
    // given
    WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
    Instant slotAt = Instant.parse("2026-08-24T12:00:00Z");
    Instant olderForecastedAt = Instant.parse("2026-08-24T05:00:00Z"); // 14시(KST) 배치
    Instant newerForecastedAt = Instant.parse("2026-08-24T08:00:00Z"); // 17시(KST) 배치(더 최신)
    WeatherForecastSlotDto slotDto = new WeatherForecastSlotDto(
        LocalDate.of(2026, 8, 24), slotAt, SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 0.0, 50.0, 20.0, 15.0, 25.0, 2.0);

    // when - 17시(최신) 먼저 저장 → 14시(더 이전)가 뒤늦게 도착해 같은 슬롯에 upsert
    weatherWriter.saveSlots(weatherGrid, newerForecastedAt, List.of(slotDto), Map.of());
    weatherWriter.saveSlots(weatherGrid, olderForecastedAt, List.of(slotDto), Map.of());

    // then - forecasted_at이 과거로 역행하지 않는다
    Weather saved = weatherRepository
        .findByWeatherGridAndForecastAtAndForecastedAt(weatherGrid, slotAt, newerForecastedAt)
        .orElseThrow();
    assertThat(saved.getForecastedAt()).isEqualTo(newerForecastedAt);
  }

  @Test
  @DisplayName("가드로_건너뛴_슬롯도_saveSlots_반환값에_현재_상태_그대로_포함된다")
  void 가드로_건너뛴_슬롯도_saveSlots_반환값에_현재_상태_그대로_포함된다() {
    // given
    WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
    Instant slotAt = Instant.parse("2026-08-24T12:00:00Z");
    Instant olderForecastedAt = Instant.parse("2026-08-24T05:00:00Z");
    Instant newerForecastedAt = Instant.parse("2026-08-24T08:00:00Z");
    WeatherForecastSlotDto slotDto = new WeatherForecastSlotDto(
        LocalDate.of(2026, 8, 24), slotAt, SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 0.0, 50.0, 20.0, 15.0, 25.0, 2.0);
    weatherWriter.saveSlots(weatherGrid, newerForecastedAt, List.of(slotDto), Map.of());

    // when - 이미 반영된 최신값보다 오래된 forecastedAt으로 같은 슬롯을 다시 저장 시도(가드에 걸려
    // 실제 upsert는 안 됨)
    List<Weather> result = weatherWriter.saveSlots(weatherGrid, olderForecastedAt,
        List.of(slotDto), Map.of());

    // then - 가드로 건너뛴 슬롯도 결과에서 빠지지 않고 현재(최신) 상태로 반환된다 - 호출부가
    // 빈 리스트로 오판해 재조회 실패로 처리하는 것을 방지한다
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getForecastedAt()).isEqualTo(newerForecastedAt);
  }

  @Test
  @DisplayName("같은_슬롯을_다른_값으로_재저장하면_current는_갱신되고_baseline은_최초값을_유지한다")
  void 같은_슬롯을_다른_값으로_재저장하면_current는_갱신되고_baseline은_최초값을_유지한다() {
    // given
    WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
    Instant slotAt = Instant.parse("2026-08-24T12:00:00Z");
    Instant forecastedAt1 = Instant.parse("2026-08-24T05:00:00Z");
    Instant forecastedAt2 = Instant.parse("2026-08-24T08:00:00Z"); // 더 최신 - upsert 가드 통과
    WeatherForecastSlotDto first = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
        .set("date", LocalDate.of(2026, 8, 24))
        .set("slotAt", slotAt)
        .set("skyStatus", SkyStatus.CLEAR)
        .set("precipitationType", PrecipitationType.NONE)
        .set("temperatureCurrent", 20.0)
        .sample();
    WeatherForecastSlotDto second = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
        .set("date", LocalDate.of(2026, 8, 24))
        .set("slotAt", slotAt)
        .set("skyStatus", SkyStatus.CLEAR)
        .set("precipitationType", PrecipitationType.NONE)
        .set("temperatureCurrent", 23.0) // temperatureCurrent만 20.0 -> 23.0
        .sample();

    // when
    weatherWriter.saveSlots(weatherGrid, forecastedAt1, List.of(first), Map.of());
    weatherWriter.saveSlots(weatherGrid, forecastedAt2, List.of(second), Map.of());

    // then
    List<Weather> all = weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(
        weatherGrid, slotAt.minus(1, java.time.temporal.ChronoUnit.DAYS));
    assertThat(all).hasSize(1);
    Weather saved = all.get(0);
    assertThat(saved.getTemperatureCurrent()).isEqualTo(23.0);
    assertThat(saved.getBaselineTemperatureCurrent()).isEqualTo(20.0);
  }
}