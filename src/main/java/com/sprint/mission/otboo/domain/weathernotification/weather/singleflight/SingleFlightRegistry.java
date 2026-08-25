package com.sprint.mission.otboo.domain.weathernotification.weather.singleflight;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SingleFlightRegistry implements MessageListener {

  private static final Duration LOCK_TTL = Duration.ofSeconds(10);
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
  private final Map<String, CompletableFuture<String>> waiters = new ConcurrentHashMap<>();

  public <T> CompletableFuture<T> execute(
      String key, Supplier<T> work, Executor executor, Supplier<Optional<T>> reload) {
    String lockKey = "lock:" + key;
    String token = UUID.randomUUID().toString(); // acquire마다 새 토큰 - 같은 인스턴스 재시도도 구분
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);

    if (Boolean.TRUE.equals(acquired)) {
      return CompletableFuture.supplyAsync(work, executor)
          .whenComplete((result, ex) -> {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
            redisTemplate.convertAndSend(CHANNEL_PREFIX + key, ex != null ? "failed" : "done");
          });
    }

    return reload.get()
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> waitForSignal(key).thenCompose(signal ->
            "failed".equals(signal)
                ? execute(key, work, executor, reload) // 리더가 실패했고 락은 이미 풀려있음 - 내가 새 리더로 재시도
                : CompletableFuture.completedFuture(reload.get().orElse(null))));
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