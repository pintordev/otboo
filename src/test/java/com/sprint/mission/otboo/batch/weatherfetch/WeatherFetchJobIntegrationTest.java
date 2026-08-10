package com.sprint.mission.otboo.batch.weatherfetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.dao.TransientDataAccessResourceException;
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

      // when - 서로 다른 JobParameters로 두 번 실행. 같은 baseTime 안에서는 Reader의 baseTime
      // 필터(작업 순서 A 4번)가 이미 저장된 격자를 재조회 대상에서 제외하므로, 두 번째 실행은
      // 신규 insert 없이 그대로 COMPLETED된다(중복 KMA 호출·중복 저장 방지가 의도된 동작)
      JobExecution firstExecution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());
      JobExecution secondExecution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(firstExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(secondExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(2);
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

      // then - retryLimit(3)은 최초 시도 포함 4회 시도를 의미. weatherFetchStep에서 4회 시도 후
      // skip되고, weatherFetchRetryStep이 같은 격자를 다시 읽어 또 4회 시도 후 skip(최소 8회).
      // 정확히 8회가 아니라 atLeast(8)인 이유 - 이 테스트만 유일하게 청크 안에 격자 2개
      // (60,127 항상 성공 / 61,128 항상 실패)가 함께 들어간다. Spring Batch의 fault-tolerant
      // 청크 처리는 retry가 소진된 뒤 실패 항목을 격리하려고 내부적으로 청크를 재처리할 수 있는데,
      // 이 과정에서 이미 성공한 60,127까지 다시 처리 대상에 들어가면서 61,128의 재시도 횟수가
      // 8보다 커질 수 있다(CI에서 실제로 13회 관측됨, 로컬에서는 항상 정확히 8회라 재현은 못 함 -
      // 격자 1개짜리 청크를 쓰는 다른 재시도 테스트들은 이 모호성이 없어 안전함). 8은 하한선
      // (retryLimit이 실제로 작동했다는 증거)으로만 검증하고, 그 이상의 내부 재처리 횟수는
      // 이 테스트의 관심사가 아니다.
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(1);
      // atLeast(8)만으로는 weatherFetchStep 혼자 retryLimit을 넘겨 8회를 채운 경우와
      // weatherFetchRetryStep까지 실제로 실행된 경우를 구분하지 못한다 - 두 Step이 모두
      // 실행됐다는 계약 자체를 명시적으로 검증한다
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactlyInAnyOrder("weatherFetchStep", "weatherFetchRetryStep");
      verify(kmaForecastFetcher, atLeast(8)).fetch(eq(new KmaGridPoint(61, 128)), any(), any());
    }

    @Test
    @DisplayName("skipLimit을_초과하면_Job이_FAILED로_끝나고_weatherFetchRetryStep은_실행되지_않는다")
    void skipLimit을_초과하면_Job이_FAILED로_끝나고_weatherFetchRetryStep은_실행되지_않는다() throws Exception {
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
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactly("weatherFetchStep");
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
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactly("weatherFetchStep");
    }
  }

  @Nested
  @DisplayName("재시도")
  class Retry {

    @Test
    @DisplayName("retryLimit_이내에_복구되면_skip되지_않고_정상_저장된다")
    void retryLimit_이내에_복구되면_skip되지_않고_정상_저장된다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      KmaGridPoint grid = new KmaGridPoint(60, 127);

      given(kmaForecastFetcher.fetch(eq(grid), any(), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willReturn(List.of(forecast()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(1);
      verify(kmaForecastFetcher, times(3)).fetch(eq(grid), any(), any());
    }

    @Test
    @DisplayName("Step1에서_skip된_격자는_weatherFetchRetryStep이_다시_시도해_최종_저장된다")
    void Step1에서_skip된_격자는_weatherFetchRetryStep이_다시_시도해_최종_저장된다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      KmaGridPoint grid = new KmaGridPoint(60, 127);

      // retryLimit(3)은 최초 시도 포함 4회 시도를 의미 - Step1에서 4회 모두 실패해 skip되고,
      // Step2(재시도)의 첫 시도(5번째 호출)에서 성공
      given(kmaForecastFetcher.fetch(eq(grid), any(), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willReturn(List.of(forecast()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactlyInAnyOrder("weatherFetchStep", "weatherFetchRetryStep");
      assertThat(weatherRepository.count()).isEqualTo(1);
      verify(kmaForecastFetcher, times(5)).fetch(eq(grid), any(), any());
    }
  }

  @Nested
  @DisplayName("DB 저장 실패")
  class DbFailure {

    @MockitoBean
    private WeatherRepository weatherRepository;

    @Test
    @DisplayName("TransientDataAccessException이_retryLimit만큼_재시도되고도_회복되지_않으면_skip되지_않고_Job이_FAILED로_끝난다")
    void TransientDataAccessException이_retryLimit만큼_재시도되고도_회복되지_않으면_skip되지_않고_Job이_FAILED로_끝난다()
        throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));

      given(weatherRepository.findLatestRevisions(any(), any())).willReturn(List.of());
      given(weatherRepository.insertIfAbsent(any(), any(), any(), any(), anyString(), anyString(),
          anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
          anyDouble(), anyDouble(), anyDouble(), anyString()))
          .willThrow(new TransientDataAccessResourceException("DB 커넥션 풀 고갈 시뮬레이션"));
      given(kmaForecastFetcher.fetch(any(), any(), any())).willReturn(List.of(forecast()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then - 저장(WeatherFetchWriter.insertIfAbsent)은 skip 대상이 아니라 재시도만 하고
      // 소진되면 즉시 FAILED. retryLimit(3)은 최초 시도 포함 4회 시도를 의미
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
      verify(weatherRepository, times(4)).insertIfAbsent(any(), any(), any(), any(), anyString(),
          anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
          anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
    }
  }
}