package com.sprint.mission.otboo.batch.weatherfetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
class WeatherFetchJobIntegrationTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  private Job weatherFetchJob;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private WeatherRepository weatherRepository;

  @MockitoBean
  private KmaForecastFetcher kmaForecastFetcher;

  @BeforeEach
  void setUp() {
    weatherRepository.deleteAll();
    weatherGridRepository.deleteAll();
    jobOperatorTestUtils.setJob(weatherFetchJob);
  }

  @AfterEach
  void tearDown() {
    // @SpringBootTest는 @DataJpaTest와 달리 트랜잭션이 자동 롤백되지 않고 실제 커밋되므로,
    // 같은 Testcontainers DB를 공유하는 다른 테스트 클래스가 이 테스트의 잔여 데이터와
    // 충돌하지 않도록 종료 시점에도 정리한다
    weatherRepository.deleteAll();
    weatherGridRepository.deleteAll();
  }

  private DailyWeatherForecastDto forecast() {
    // skyStatus/precipitationType은 DB NOT NULL 컬럼이라 FixtureMonkey 랜덤 생성에 맡기지 않고
    // 명시적으로 고정한다 - 랜덤 생성 시 null이 나오면 제약 위반으로 flaky하게 실패했었음
    return FIXTURE_MONKEY.giveMeBuilder(DailyWeatherForecastDto.class)
        .set("date", LocalDate.now())
        .set("skyStatus", SkyStatus.CLEAR)
        .set("precipitationType", PrecipitationType.NONE)
        .sample();
  }

  @Nested
  @DisplayName("WeatherFetchJob")
  class Run {

    @Test
    @DisplayName("등록된_WeatherGrid마다_Weather를_새로_저장하고_COMPLETED로_끝난다")
    void 등록된_WeatherGrid마다_Weather를_새로_저장하고_COMPLETED로_끝난다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetch(any(), any(), any())).willReturn(List.of(forecast()));

      // when - 서로 다른 JobParameters로 두 번 실행해 매 실행마다 새 행이 insert되는지(update 아님) 확인
      JobExecution firstExecution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());
      JobExecution secondExecution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(firstExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(secondExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("일부_격자가_실패해도_skip되고_Job은_COMPLETED로_끝난다")
    void 일부_격자가_실패해도_skip되고_Job은_COMPLETED로_끝난다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetch(any(), any(), any())).willReturn(List.of(forecast()));
      given(kmaForecastFetcher.fetch(eq(new KmaGridPoint(61, 128)), any(), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("skipLimit을_초과하면_Job이_FAILED로_끝난다")
    void skipLimit을_초과하면_Job이_FAILED로_끝난다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetch(any(), any(), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
      assertThat(weatherRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("복구_불가능한_예외는_skip하지_않고_1건만_실패해도_Job이_즉시_FAILED로_끝난다")
    void 복구_불가능한_예외는_skip하지_않고_1건만_실패해도_Job이_즉시_FAILED로_끝난다() throws Exception {
      // given - skipLimit(1)로도 봐줄 수 있는 횟수지만, KmaApiException이 아니라 skip 대상이 아님
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetch(any(), any(), any())).willReturn(List.of(forecast()));
      given(kmaForecastFetcher.fetch(eq(new KmaGridPoint(61, 128)), any(), any()))
          .willThrow(new IllegalStateException("DB/설정 오류 등 복구 불가능한 예외 시뮬레이션"));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }
  }
}
