package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
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
class WeatherWriterUpsertGuardTest {

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
}