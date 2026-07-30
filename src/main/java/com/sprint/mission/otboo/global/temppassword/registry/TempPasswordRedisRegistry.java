package com.sprint.mission.otboo.global.temppassword.registry;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TempPasswordRedisRegistry implements TempPasswordRegistry {

  private static final String KEY_PREFIX = "auth:temp-password:";
  private static final Duration TTL = Duration.ofMinutes(3);

  private final StringRedisTemplate redisTemplate;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void save(UUID userId, String rawTempPassword) {
    redisTemplate.opsForValue().set(key(userId), passwordEncoder.encode(rawTempPassword), TTL);
  }

  @Override
  public void revoke(UUID userId) {
    redisTemplate.delete(key(userId));
  }

  @Override
  public boolean matches(UUID userId, String rawPassword) {
    String saved = redisTemplate.opsForValue().get(key(userId));
    return saved != null && passwordEncoder.matches(rawPassword, saved);
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
