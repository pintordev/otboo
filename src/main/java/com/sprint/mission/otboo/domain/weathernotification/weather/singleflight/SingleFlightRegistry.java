package com.sprint.mission.otboo.domain.weathernotification.weather.singleflight;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.SingleFlightProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SingleFlightRegistry implements MessageListener {

  // 리더가 계속 실패할 때 무한 재귀로 락 획득·외부 호출을 반복하지 않도록 두는 재시도 한도
  private static final int MAX_RETRIES = 3;
  private static final String CHANNEL_PREFIX = "single-flight:";
  // 내가 건 락만 지우는 compare-and-delete - GET한 값이 인자로 준 토큰과 같을 때만 DEL(원자적)
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('del', KEYS[1])
      else
        return 0
      end
      """, Long.class);

  private final StringRedisTemplate redisTemplate;
  // work(외부 API 호출 + 저장)가 이 시간 안에 끝난다는 전제가 있다 - lease 갱신은 하지 않으므로,
  // kma/kakao Feign 타임아웃 합이 이 값보다 충분히 짧아야 한다(SingleFlightLeaseTimeoutValidator가
  // 기동 시점에 검증).
  private final Duration lockTtl;
  // 신호 유실(lost wakeup) 시에도 future가 반드시 완료되도록 두는 대기 상한 - 락 TTL보다 넉넉하게 잡는다
  private final Duration waitTimeout;
  private final Map<String, CompletableFuture<String>> waiters = new ConcurrentHashMap<>();

  public SingleFlightRegistry(StringRedisTemplate redisTemplate,
      SingleFlightProperties singleFlightProperties) {
    this.redisTemplate = redisTemplate;
    this.lockTtl = singleFlightProperties.lockTtl();
    this.waitTimeout = lockTtl.plusSeconds(5);
  }

  public <T> CompletableFuture<T> execute(
      String key, Supplier<T> work, Executor executor, Supplier<Optional<T>> reload) {
    return execute(key, work, executor, reload, 0);
  }

  private <T> CompletableFuture<T> execute(
      String key, Supplier<T> work, Executor executor, Supplier<Optional<T>> reload,
      int attempt) {
    String lockKey = "lock:" + key;
    String token = UUID.randomUUID().toString(); // acquire마다 새 토큰 - 같은 인스턴스 재시도도 구분
    // reload 확인과 waiter 등록 사이에 리더의 done 발행이 끼면 신호를 영영 못 받으므로,
    // 락 시도보다도 먼저 waiter부터 등록해 둔다
    CompletableFuture<String> signal = waitForSignal(key);
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);

    if (Boolean.TRUE.equals(acquired)) {
      waiters.remove(key, signal); // 내가 리더면 이 waiter는 필요 없다
      return CompletableFuture.supplyAsync(work, executor)
          .whenComplete((result, ex) -> {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
            redisTemplate.convertAndSend(CHANNEL_PREFIX + key, ex != null ? "failed" : "done");
          });
    }

    Optional<T> reloaded = reload.get();
    if (reloaded.isPresent()) {
      waiters.remove(key, signal);
      return CompletableFuture.completedFuture(reloaded.get());
    }
    // 신호가 유실돼도 이 future는 반드시 완료되게 대기 상한을 둔다
    CompletableFuture<String> boundedSignal =
        signal.completeOnTimeout("timeout", waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    // onMessage가 이미 지웠다면 no-op, 신호 유실로 타임아웃됐다면 여기서 정리한다.
    // 조건부 remove라 그 사이 재등록된 새 waiter는 건드리지 않는다.
    boundedSignal.whenComplete((ignored, ex) -> waiters.remove(key, signal));
    return boundedSignal
        .thenCompose(received -> {
          if (!"failed".equals(received)) {
            return CompletableFuture.completedFuture(reload.get().orElse(null));
          }
          if (attempt >= MAX_RETRIES) {
            return CompletableFuture.<T>failedFuture(new IllegalStateException(
                "single-flight 리더가 반복 실패해 재시도 한도를 초과함: key=" + key));
          }
          // 리더가 실패했고 락은 이미 풀려있음 - 내가 새 리더로 재시도
          return execute(key, work, executor, reload, attempt + 1);
        });
  }

  private CompletableFuture<String> waitForSignal(String key) {
    return waiters.computeIfAbsent(key, k -> new CompletableFuture<>());
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String channel = new String(message.getChannel());
      String key = channel.substring(CHANNEL_PREFIX.length());
      CompletableFuture<String> waiter = waiters.remove(key);
      if (waiter != null) {
        waiter.complete(new String(message.getBody()));
      }
    } catch (Exception e) {
      log.error("single-flight 완료 메시지 처리 실패: channel={}", new String(message.getChannel()), e);
    }
  }
}