package com.sprint.mission.otboo.global.temppassword.registry.impl;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordProperties;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TempPasswordRedisRegistry implements TempPasswordRegistry {

  private static final String KEY_PREFIX = "auth:temp-password:";

  private final StringRedisTemplate redisTemplate;
  private final TempPasswordProperties tempPasswordProperties;
  private final TempPasswordGenerator tempPasswordGenerator;
  private final PasswordEncoder passwordEncoder;

  public TempPasswordRedisRegistry(
      StringRedisTemplate redisTemplate,
      TempPasswordProperties tempPasswordProperties,
      TempPasswordGenerator tempPasswordProvider,
      PasswordEncoder passwordEncoder
  ) {
    this.redisTemplate = redisTemplate;
    this.tempPasswordProperties = tempPasswordProperties;
    this.tempPasswordGenerator = tempPasswordProvider;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public TempPasswordGenerator generator() {
    return tempPasswordGenerator;
  }

  @Override
  public void save(UUID userId, String rawTempPassword) {
    redisTemplate.opsForValue().set(key(userId), passwordEncoder.encode(rawTempPassword),
        tempPasswordProperties.expiration());
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

  @Override
  public int getExpirationMinutes() {
    return (int) tempPasswordProperties.expiration().toMinutes();
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
