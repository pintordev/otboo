package com.sprint.mission.otboo.domain.weathernotification.weather.singleflight;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.SingleFlightConfig;
import com.sprint.mission.otboo.global.config.AsyncConfig;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {
    DataRedisAutoConfiguration.class, AsyncConfig.class, SingleFlightConfig.class,
    SingleFlightRegistry.class
})
class SingleFlightRegistryTest implements RedisTestContainerSupport {

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @Autowired
  private SingleFlightRegistry registry;
  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    // 이전 테스트가 예외로 중단돼도 상태가 남지 않도록 시작 시점에도 비워 둔다
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @AfterEach
  void cleanUp() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @Nested
  @DisplayName("execute")
  class Execute {

    @Test
    @DisplayName("락을_획득한_쪽만_work를_실행하고_완료_후_락을_해제한다")
    void 락을_획득한_쪽만_work를_실행하고_완료_후_락을_해제한다() throws Exception {
      // given
      AtomicInteger callCount = new AtomicInteger();
      Supplier<String> work = () -> {
        callCount.incrementAndGet();
        return "done";
      };

      // when
      String result = registry.execute("test-key", work, Runnable::run,
          () -> Optional.<String>empty()).get(2, TimeUnit.SECONDS);

      // then
      assertThat(result).isEqualTo("done");
      assertThat(callCount.get()).isEqualTo(1);
      assertThat(redisTemplate.hasKey("lock:test-key")).isFalse();
    }

    @Test
    @DisplayName("락_획득에_실패하면_reload로_더블체크하고_있으면_바로_반환한다")
    void 락_획득에_실패하면_reload로_더블체크하고_있으면_바로_반환한다() throws Exception {
      // given - 다른 인스턴스가 이미 락을 쥐고 있는 상황을 흉내
      redisTemplate.opsForValue()
          .setIfAbsent("lock:test-key", "other-instance", Duration.ofSeconds(10));
      Supplier<String> work = () -> {
        throw new AssertionError("리더가 아니므로 호출되면 안 됨");
      };

      // when
      String result = registry.execute("test-key", work, Runnable::run,
          () -> Optional.of("already-done")).get(2, TimeUnit.SECONDS);

      // then
      assertThat(result).isEqualTo("already-done");
    }

    @Test
    @DisplayName("락_획득에_실패하고_더블체크에도_없으면_done_메시지를_기다렸다가_reload한다")
    void 락_획득에_실패하고_더블체크에도_없으면_done_메시지를_기다렸다가_reload한다() throws Exception {
      // given - 이미 락을 쥔 "리더" 시뮬레이션: 잠시 후 스스로 락을 해제하고 done을 발행
      redisTemplate.opsForValue().setIfAbsent("lock:test-key", "leader", Duration.ofSeconds(10));
      AtomicBoolean reloaded = new AtomicBoolean(false);
      ScheduledExecutorService leader = Executors.newSingleThreadScheduledExecutor();
      try {
        leader.schedule(() -> {
          redisTemplate.delete("lock:test-key");
          redisTemplate.convertAndSend("single-flight:test-key", "done");
        }, 300, TimeUnit.MILLISECONDS);

        // when - reload는 처음엔 없다가, done 수신 후에는 있다고 응답
        String result = registry.execute("test-key", () -> "unused", Runnable::run, () -> {
          if (reloaded.getAndSet(true)) {
            return Optional.of("reloaded-after-done");
          }
          return Optional.empty();
        }).get(3, TimeUnit.SECONDS);

        // then
        assertThat(result).isEqualTo("reloaded-after-done");
      } finally {
        leader.shutdownNow();
      }
    }
  }
}