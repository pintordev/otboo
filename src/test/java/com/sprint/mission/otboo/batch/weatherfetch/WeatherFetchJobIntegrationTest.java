package com.sprint.mission.otboo.batch.weatherfetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.authuser.user.entity.Location;
import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.WeatherChangeNotificationLogRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.exception.KmaApiException;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
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

  private WeatherForecastSlotDto forecast() {
    // skyStatus/precipitationType은 DB NOT NULL 컬럼이라 FixtureMonkey 랜덤 생성에 맡기지 않고
    // 명시적으로 고정한다 - 랜덤 생성 시 null이 나오면 제약 위반으로 flaky하게 실패했었음
    LocalDate today = LocalDate.now();
    return FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
        .set("date", today)
        .set("slotAt", today.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant())
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

      given(kmaForecastFetcher.fetchSlots(any(), any())).willReturn(List.of(forecast()));

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

      given(kmaForecastFetcher.fetchSlots(any(), any())).willReturn(List.of(forecast()));
      given(kmaForecastFetcher.fetchSlots(eq(new KmaGridPoint(61, 128)), any()))
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
      verify(kmaForecastFetcher, atLeast(8)).fetchSlots(eq(new KmaGridPoint(61, 128)), any());
    }

    @Test
    @DisplayName("skipLimit을_초과해_weatherFetchStep이_FAILED여도_weatherFetchRetryStep이_실행된다")
    void skipLimit을_초과해_weatherFetchStep이_FAILED여도_weatherFetchRetryStep이_실행된다()
        throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetchSlots(any(), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then - weatherFetchStep이 skipLimit 초과로 FAILED여도 .on("*")으로 weatherFetchRetryStep까지
      // 실행돼야 한다(재시도가 가장 필요한 순간에 실행되지 않던 기존 버그).
      // 두 Step 모두 같은 격자에 계속 실패하므로 최종 Job 상태는 여전히 FAILED다.
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
      assertThat(weatherRepository.count()).isEqualTo(0);
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactlyInAnyOrder("weatherFetchStep", "weatherFetchRetryStep");
    }

    @Test
    @DisplayName("복구_불가능한_예외는_skip하지_않고_1건만_실패해도_Job이_즉시_FAILED로_끝난다")
    void 복구_불가능한_예외는_skip하지_않고_1건만_실패해도_Job이_즉시_FAILED로_끝난다() throws Exception {
      // given - skipLimit(1)로도 봐줄 수 있는 횟수지만, KmaApiException이 아니라 skip 대상이 아님
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherGridRepository.save(WeatherGrid.create(61, 128));

      given(kmaForecastFetcher.fetchSlots(any(), any())).willReturn(List.of(forecast()));
      given(kmaForecastFetcher.fetchSlots(eq(new KmaGridPoint(61, 128)), any()))
          .willThrow(new IllegalStateException("DB/설정 오류 등 복구 불가능한 예외 시뮬레이션"));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then - .on("*")는 실패 원인을 가리지 않으므로 복구 불가능한 예외로 즉시 FAILED여도
      // weatherFetchRetryStep까지 실행된다(같은 격자를 다시 시도하다 마찬가지로 즉시 FAILED)
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
      assertThat(execution.getStepExecutions()).extracting(se -> se.getStepName())
          .containsExactlyInAnyOrder("weatherFetchStep", "weatherFetchRetryStep");
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

      given(kmaForecastFetcher.fetchSlots(eq(grid), any()))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willThrow(KmaApiException.of("03", "NO_DATA"))
          .willReturn(List.of(forecast()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(weatherRepository.count()).isEqualTo(1);
      verify(kmaForecastFetcher, times(3)).fetchSlots(eq(grid), any());
    }

    @Test
    @DisplayName("Step1에서_skip된_격자는_weatherFetchRetryStep이_다시_시도해_최종_저장된다")
    void Step1에서_skip된_격자는_weatherFetchRetryStep이_다시_시도해_최종_저장된다() throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      KmaGridPoint grid = new KmaGridPoint(60, 127);

      // retryLimit(3)은 최초 시도 포함 4회 시도를 의미 - Step1에서 4회 모두 실패해 skip되고,
      // Step2(재시도)의 첫 시도(5번째 호출)에서 성공
      given(kmaForecastFetcher.fetchSlots(eq(grid), any()))
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
      verify(kmaForecastFetcher, times(5)).fetchSlots(eq(grid), any());
    }
  }

  @Nested
  @DisplayName("DB 저장 실패")
  class DbFailure {

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("TransientDataAccessException이_retryLimit만큼_재시도되고도_회복되지_않으면_skip되지_않고_Job이_FAILED로_끝난다")
    void TransientDataAccessException이_retryLimit만큼_재시도되고도_회복되지_않으면_skip되지_않고_Job이_FAILED로_끝난다()
        throws Exception {
      // given
      weatherGridRepository.save(WeatherGrid.create(60, 127));

      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willThrow(new TransientDataAccessResourceException("DB 커넥션 풀 고갈 시뮬레이션"));
      given(kmaForecastFetcher.fetchSlots(any(), any())).willReturn(List.of(forecast()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then - 저장(WeatherFetchWriter.batchUpdate)은 skip 대상이 아니라 재시도만 하고
      // 소진되면 즉시 FAILED. retryLimit(3)은 최초 시도 포함 4회 시도를 의미하고, weatherFetchStep이
      // FAILED여도 weatherFetchRetryStep이 같은 격자를 다시 4회 시도하므로 총 8회다
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
      verify(jdbcTemplate, times(8)).batchUpdate(anyString(),
          any(BatchPreparedStatementSetter.class));
    }
  }

  @Nested
  @DisplayName("날씨 급변 알림")
  class SuddenChangeNotification {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 2026-08-12 09:10 KST(버퍼 20분 적용 시 08:50) - baseTime "0800"으로 고정해 D0가
    // 평가 대상에서 빠지는 23:30 회차를 피한다. 실제 시각과 무관하게 결정적으로 재현.
    private static final Instant FIXED_NOW = Instant.parse("2026-08-12T00:10:00Z");

    @TestBean
    private Clock clock;

    static Clock clock() {
      // Mockito mock 대신 실제 고정 Clock을 반환 - millis()/getZone() 등 다른 메서드가
      // 호출돼도 mock 기본값(null/0) 대신 정상 동작한다(CodeRabbit PR #131 리뷰)
      return Clock.fixed(FIXED_NOW, KST);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WeatherChangeNotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUpNotification() {
      // 외부 클래스 setUp()은 weather/weatherGrid만 비운다 - 이전 실행이 중간에 죽으면
      // users/profiles가 남아 356행의 고정 이메일이 유니크 제약에 걸릴 수 있어 시작 시에도 정리
      cleanUpNotificationTables();
    }

    @AfterEach
    void tearDownNotification() {
      cleanUpNotificationTables();
    }

    private void cleanUpNotificationTables() {
      // 외부 클래스 tearDown()의 weatherGridRepository.deleteAll()보다 먼저 정리해야
      // weather_change_notification_logs의 FK 위반 없이 격자를 지울 수 있다
      notificationLogRepository.deleteAll();
      notificationRepository.deleteAll();
      profileRepository.deleteAll();
      userRepository.deleteAll();
    }

    @Disabled("V14(유니크 제약 (weather_grid_id, forecast_at)) 이후 리비전 개념이 사라져 "
        + "revisions.size()<2가 상시 참 - #163에서 baseline 컬럼 비교 방식으로 재설계 후 재작성")
    @Test
    @DisplayName("이전_리비전_대비_기온이_급변하면_Job_COMPLETED_후_비동기로_알림이_저장된다")
    void 이전_리비전_대비_기온이_급변하면_Job_COMPLETED_후_비동기로_알림이_저장된다() throws Exception {
      // given
      BaseTime currentBaseTime = KmaBaseTimeCalculator.calculate(FIXED_NOW);
      LocalDate today = LocalDate.parse(currentBaseTime.baseDate(),
          DateTimeFormatter.ofPattern("yyyyMMdd"));
      Instant forecastAt = today.atStartOfDay(KST).toInstant();
      Instant previousForecastedAt = KmaBaseTimeCalculator
          .calculate(FIXED_NOW.minus(Duration.ofHours(3))).toInstant();

      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      weatherRepository.save(Weather.create(grid, previousForecastedAt, forecastAt,
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, 20.0, 0.0, 15.0, 25.0, 2.0,
          WindStrength.WEAK, null, null, null, null));

      User user = userRepository.save(
          User.create("홍길동", "sudden-change@test.com", "encoded-password"));
      Profile profile = Profile.create(user);
      profile.changeProfile(null, null,
          Location.create(37.5, 127.0, 60, 127, List.of("서울특별시", "강남구")), 3);
      profileRepository.save(profile);

      given(kmaForecastFetcher.fetchSlots(any(), any())).willReturn(List.of(
          FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
              .set("date", today)
              .set("slotAt", today.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant())
              .set("skyStatus", SkyStatus.CLEAR)
              .set("precipitationType", PrecipitationType.NONE)
              .set("temperatureCurrent", 25.0)
              .sample()));

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          jobOperatorTestUtils.getUniqueJobParameters());

      // then - 20.0도 -> 25.0도(임계값 3.0도 이상) 급변, afterJob()이 COMPLETED 이후 감지·발행하고
      // NotificationRequestedEventListener가 AFTER_COMMIT + @Async로 저장을 마칠 때까지 대기한다
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
          assertThat(notificationRepository.findAll())
              .anySatisfy(notification -> {
                assertThat(notification.getReceiverId()).isEqualTo(user.getId());
                assertThat(notification.getContent()).startsWith("강남구 ");
              }));
    }
  }
}