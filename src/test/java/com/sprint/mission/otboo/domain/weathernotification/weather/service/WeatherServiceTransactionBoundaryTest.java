package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class WeatherServiceTransactionBoundaryTest extends IntegrationTestSupport {

  @Autowired
  private WeatherService weatherService;
  @Autowired
  private WeatherGridRepository weatherGridRepository;
  @MockitoBean
  private WeatherRefresher weatherRefresher;
  @MockitoBean
  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    weatherGridRepository.deleteAll();
  }

  @AfterEach
  void tearDown() {
    // @SpringBootTest는 @DataJpaTest와 달리 트랜잭션이 자동 롤백되지 않고 실제 커밋되므로,
    // 같은 Testcontainers DB를 공유하는 다른 테스트 클래스가 이 테스트의 잔여 데이터와
    // 충돌하지 않도록 종료 시점에도 정리한다
    weatherGridRepository.deleteAll();
  }

  @Test
  @DisplayName("stale_재조회_중에는_활성_트랜잭션을_보유하지_않는다")
  void stale_재조회_중에는_활성_트랜잭션을_보유하지_않는다() {
    // given - DB에 오늘 슬롯이 전혀 없어 항상 stale로 판정됨
    AtomicBoolean transactionActiveDuringRefresh = new AtomicBoolean();
    given(weatherRefresher.refreshSlots(any(), any(), any())).willAnswer(invocation -> {
      transactionActiveDuringRefresh.set(TransactionSynchronizationManager.isActualTransactionActive());
      return List.of();
    });
    given(locationResolver.resolveWeatherGrid(any()))
        .willReturn(weatherGridRepository.save(WeatherGrid.create(60, 127)));
    given(locationResolver.resolveLocationNames(anyDouble(), anyDouble())).willReturn(List.of());

    // when
    weatherService.getWeather(37.5, 127.0);

    // then - 클래스 레벨 readOnly 트랜잭션이 걸려 있다면 이 값은 true다
    assertThat(transactionActiveDuringRefresh.get()).isFalse();
  }
}