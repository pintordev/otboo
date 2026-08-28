package com.sprint.mission.otboo.batch.weatherretention;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
class WeatherRetentionJobIntegrationTest extends IntegrationTestSupport {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  @Qualifier("weatherRetentionJob")
  private Job weatherRetentionJob;

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private WeatherD1BaselineRepository weatherD1BaselineRepository;

  @BeforeEach
  void setUp() {
    cleanUpWeatherTables();
    jobOperatorTestUtils.setJob(weatherRetentionJob);
  }

  @AfterEach
  void tearDown() {
    // @SpringBootTest는 @DataJpaTest와 달리 트랜잭션이 자동 롤백되지 않고 실제 커밋되므로,
    // 같은 Testcontainers DB를 공유하는 다른 테스트 클래스가 이 테스트의 잔여 데이터와
    // 충돌하지 않도록 종료 시점에도 정리한다
    cleanUpWeatherTables();
  }

  private void cleanUpWeatherTables() {
    weatherD1BaselineRepository.deleteAll();
    weatherRepository.deleteAll();
    weatherGridRepository.deleteAll();
  }

  private Weather weatherOf(WeatherGrid weatherGrid, LocalDate forecastDate) {
    Instant forecastAt = forecastDate.atStartOfDay(KST).toInstant();
    return Weather.create(weatherGrid, forecastAt.plusSeconds(1), forecastAt, SkyStatus.CLEAR,
        PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, 20.0, 0.0, 15.0, 25.0, 2.0,
        WindStrength.WEAK, 20.0, PrecipitationType.NONE, 0.0, 0.0, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
  }

  @Nested
  @DisplayName("WeatherRetentionJob")
  class Run {

    @Test
    @DisplayName("cutoff보다_오래된_행만_삭제하고_최근_미래_행은_보존한다")
    void cutoff보다_오래된_행만_삭제하고_최근_미래_행은_보존한다() throws Exception {
      // given - 테스트 프로파일 retention-days=7 기준
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      LocalDate today = LocalDate.now(KST);

      Weather old = weatherRepository.save(weatherOf(grid, today.minusDays(10)));
      Weather recent = weatherRepository.save(weatherOf(grid, today.minusDays(3)));
      Weather future = weatherRepository.save(weatherOf(grid, today.plusDays(1)));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      List<UUID> remainingIds = weatherRepository.findAll().stream().map(Weather::getId).toList();
      assertThat(remainingIds).containsExactlyInAnyOrder(recent.getId(), future.getId());
      assertThat(remainingIds).doesNotContain(old.getId());
    }

    @Test
    @DisplayName("chunkSize보다_많은_삭제_대상도_여러_청크에_걸쳐_전부_삭제된다")
    void chunkSize보다_많은_삭제_대상도_여러_청크에_걸쳐_전부_삭제된다() throws Exception {
      // given - 테스트 프로파일 chunk-size=10보다 많은 오래된 행을 생성해 청크 경계를 넘게 한다
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      LocalDate baseDate = LocalDate.now(KST).minusDays(30);
      for (int i = 0; i < 25; i++) {
        weatherRepository.save(weatherOf(grid, baseDate.minusDays(i)));
      }

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("삭제_대상이_없으면_아무것도_지우지_않고_COMPLETED로_끝난다")
    void 삭제_대상이_없으면_아무것도_지우지_않고_COMPLETED로_끝난다() throws Exception {
      // given
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      Weather recent = weatherRepository.save(weatherOf(grid, LocalDate.now(KST)));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.findById(recent.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("WeatherD1BaselineRetention")
  class WeatherD1BaselineRetention {

    @Test
    @DisplayName("target_date가_오늘_이하인_고아_baseline만_삭제하고_이후_날짜는_보존한다")
    void target_date가_오늘_이하인_고아_baseline만_삭제하고_이후_날짜는_보존한다() throws Exception {
      // given - 그리드가 중간에 배치 대상에서 빠져 소비되지 못한 고아 baseline을 흉내낸다
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      LocalDate today = LocalDate.now(KST);

      WeatherD1Baseline overdue = weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid, today.minusDays(1), Map.of(), Instant.now()));
      WeatherD1Baseline pending = weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid, today.plusDays(1), Map.of(), Instant.now()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherD1BaselineRepository.findById(overdue.getId())).isEmpty();
      assertThat(weatherD1BaselineRepository.findById(pending.getId())).isPresent();
    }
  }
}
