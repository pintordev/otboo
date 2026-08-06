package com.sprint.mission.otboo.security.usersession.registry.impl;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public class UserSessionRedisRegistry implements UserSessionRegistry {

  private static final String KEY_PREFIX = "auth:user-session:";
  private static final String FIELD_SESSION_ID = "sessionId";
  private static final String FIELD_REFRESH_JTI = "refreshJti";
  private static final String FIELD_ISSUED_AT = "issuedAt";

  private final StringRedisTemplate redisTemplate;

  public UserSessionRedisRegistry(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public UserSession save(UUID userId, UserSession session, Instant expiresAt) {
    String redisKey = key(userId);

    Map<String, String> fields = Map.of(
        FIELD_REFRESH_JTI, session.currentRefreshJti().toString(),
        FIELD_SESSION_ID, session.sessionId().toString(),
        FIELD_ISSUED_AT, session.issuedAt().toString()
    );

    redisTemplate.execute(new SessionCallback<Object>() {
      @Override
      public Object execute(RedisOperations operations) {
        operations.multi();
        operations.opsForHash().putAll(redisKey, fields);
        operations.expireAt(redisKey, expiresAt);
        return operations.exec();
      }
    });

    return session;
  }

  @Override
  public void revoke(UUID userId) {
    redisTemplate.delete(key(userId));
  }

  @Override
  public Optional<UserSession> find(UUID userId) {
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(userId));
    if (entries.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new UserSession(
        UUID.fromString((String) entries.get(FIELD_REFRESH_JTI)),
        UUID.fromString((String) entries.get(FIELD_SESSION_ID)),
        Instant.parse((String) entries.get(FIELD_ISSUED_AT))
    ));
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
