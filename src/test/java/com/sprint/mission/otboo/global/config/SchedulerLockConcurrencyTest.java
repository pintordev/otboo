package com.sprint.mission.otboo.global.config;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherfetch.scheduler.WeatherFetchScheduler;
import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherFetchService;
import com.sprint.mission.otboo.batch.weatherretention.scheduler.WeatherRetentionScheduler;
import com.sprint.mission.otboo.batch.weatherretention.service.WeatherRetentionService;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = {
    DataRedisAutoConfiguration.class, SchedulerLockConfig.class, WeatherFetchScheduler.class,
    WeatherRetentionScheduler.class
})
class SchedulerLockConcurrencyTest implements RedisTestContainerSupport {

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @Autowired
  private WeatherFetchScheduler weatherFetchScheduler;

  @MockitoBean
  private WeatherFetchService weatherFetchService;

  @Autowired
  private WeatherRetentionScheduler weatherRetentionScheduler;

  @MockitoBean
  private WeatherRetentionService weatherRetentionService;

  @Nested
  @DisplayName("WeatherFetch 락")
  class WeatherFetchLock {

    @Test
    @DisplayName("동시에 호출해도 실제 실행은 한 번뿐이다")
    void 동시에_호출해도_실제_실행은_한_번뿐이다() throws Exception {
      // given
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
        // when
        List<Future<Object>> futures = IntStream.range(0, 2)
            .<Future<Object>>mapToObj(i -> executor.submit(() -> {
              ready.countDown();
              start.await();
              weatherFetchScheduler.fetch();
              return null;
            }))
            .toList();
        ready.await();
        start.countDown();
        for (Future<Object> future : futures) {
          future.get(15, TimeUnit.SECONDS);
        }

        // then
        verify(weatherFetchService, times(1)).execute();
      } finally {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }

  @Nested
  @DisplayName("WeatherRetention 락")
  class WeatherRetentionLock {

    @Test
    @DisplayName("동시에 호출해도 실제 실행은 한 번뿐이다")
    void 동시에_호출해도_실제_실행은_한_번뿐이다() throws Exception {
      // given
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
        // when
        List<Future<Object>> futures = IntStream.range(0, 2)
            .<Future<Object>>mapToObj(i -> executor.submit(() -> {
              ready.countDown();
              start.await();
              weatherRetentionScheduler.cleanUp();
              return null;
            }))
            .toList();
        ready.await();
        start.countDown();
        for (Future<Object> future : futures) {
          future.get(15, TimeUnit.SECONDS);
        }

        // then
        verify(weatherRetentionService, times(1)).execute();
      } finally {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }
}