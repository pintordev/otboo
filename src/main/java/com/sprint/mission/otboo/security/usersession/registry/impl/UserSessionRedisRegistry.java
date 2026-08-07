package com.sprint.mission.otboo.security.usersession.registry.impl;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.business.RefreshTokenReusedException;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

public class UserSessionRedisRegistry implements UserSessionRegistry {

  private static final String SESSION_KEY_PREFIX = "auth:user-session:";
  private static final String INDEX_KEY_PREFIX = "auth:user-session-index:";

  private static final String FIELD_REFRESH_JTI = "refreshJti";
  private static final String FIELD_ISSUED_AT = "issuedAt";

  private static final RedisScript<Long> REPLACE_ALL_SCRIPT = RedisScript.of(
      new ClassPathResource("scripts/replace-all-user-sessions.lua"), Long.class);
  private static final RedisScript<Long> COMPARE_AND_ROTATE_SCRIPT = RedisScript.of(
      new ClassPathResource("scripts/compare-and-rotate.lua"), Long.class);

  private final StringRedisTemplate redisTemplate;
  private final HashOperations<String, String, String> hashOps;
  private final ZSetOperations<String, String> zSetOps;
  private final UserSessionExpirationPolicy expirationPolicy;
  private final Clock clock;

  public UserSessionRedisRegistry(StringRedisTemplate redisTemplate,
      UserSessionExpirationPolicy expirationPolicy, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.hashOps = redisTemplate.opsForHash();
    this.zSetOps = redisTemplate.opsForZSet();
    this.expirationPolicy = expirationPolicy;
    this.clock = clock;
  }

  @Override
  public UserSessionExpirationPolicy expirationPolicy() {
    return expirationPolicy;
  }

  @Override
  public UserSession save(UserSession session, Instant expiresAt) {
    String sessionKey = sessionKey(session.userId(), session.sessionId());
    String indexKey = indexKey(session.userId());

    Map<String, String> fields = Map.of(
        FIELD_REFRESH_JTI, session.currentRefreshJti().toString(),
        FIELD_ISSUED_AT, session.issuedAt().toString()
    );

    redisTemplate.execute(new SessionCallback<Object>() {
      @Override
      public Object execute(RedisOperations operations) {
        operations.multi();
        operations.opsForHash().putAll(sessionKey, fields);
        operations.expireAt(sessionKey, expiresAt);
        operations.opsForZSet().add(indexKey, session.sessionId().toString(), expiresAt.toEpochMilli());
        return operations.exec();
      }
    });

    // 인덱스 키 자체에도 TTL을 걸어서, findAllByUserId/revokeAll이 한 번도 안 불려도
    // 이 유저의 세션이 전부 만료된 뒤엔 인덱스도 저절로 사라지게 한다.
    refreshIndexExpiry(indexKey, expiresAt);

    return session;
  }

  @Override
  public UserSession replaceAll(UserSession session, Instant expiresAt) {
    String indexKey = indexKey(session.userId());
    String newSessionKey = sessionKey(session.userId(), session.sessionId());

    redisTemplate.execute(REPLACE_ALL_SCRIPT,
        List.of(indexKey, newSessionKey),
        sessionKeyPrefix(session.userId()),
        session.sessionId().toString(),
        session.currentRefreshJti().toString(),
        session.issuedAt().toString(),
        String.valueOf(expiresAt.toEpochMilli()));

    return session;
  }

  @Override
  public UserSession compareAndRotate(UUID userId, UUID sessionId, UUID expectedRefreshJti,
      UUID newRefreshJti, Instant issuedAt, Instant expiresAt) {
    String sessionKey = sessionKey(userId, sessionId);
    String indexKey = indexKey(userId);

    Long result = redisTemplate.execute(COMPARE_AND_ROTATE_SCRIPT,
        List.of(sessionKey, indexKey),
        sessionKeyPrefix(userId),
        sessionId.toString(),
        expectedRefreshJti.toString(),
        newRefreshJti.toString(),
        String.valueOf(expiresAt.toEpochMilli()));

    if (result == null || result == 0L) {
      throw UserSessionExpiredException.withNone();
    }
    if (result == -1L) {
      throw RefreshTokenReusedException.withNone();
    }

    return new UserSession(userId, sessionId, newRefreshJti, issuedAt);
  }

  @Override
  public Optional<UserSession> find(UUID userId, UUID sessionId) {
    String sessionKey = sessionKey(userId, sessionId);
    Map<String, String> fields = hashOps.entries(sessionKey);

    if (fields.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toUserSession(userId, sessionId, fields));
  }

  @Override
  public List<UserSession> findAllByUserId(UUID userId) {
    String indexKey = indexKey(userId);
    pruneExpired(indexKey);

    Set<String> sessionIds = zSetOps.range(indexKey, 0, -1);
    if (sessionIds == null || sessionIds.isEmpty()) {
      return List.of();
    }

    List<UserSession> sessions = new ArrayList<>();
    for (String rawSessionId : sessionIds) {
      // 실제 세션 키가 TTL로 먼저 사라진 경우 find()가 비어있는 채로 자연스럽게 걸러진다.
      find(userId, UUID.fromString(rawSessionId)).ifPresent(sessions::add);
    }
    return sessions;
  }

  @Override
  public void revoke(UUID userId, UUID sessionId) {
    redisTemplate.delete(sessionKey(userId, sessionId));
    zSetOps.remove(indexKey(userId), sessionId.toString());
  }

  @Override
  public void revokeAll(UUID userId) {
    String indexKey = indexKey(userId);
    Set<String> sessionIds = zSetOps.range(indexKey, 0, -1);

    if (sessionIds != null) {
      for (String rawSessionId : sessionIds) {
        redisTemplate.delete(sessionKey(userId, UUID.fromString(rawSessionId)));
      }
      // 읽은 멤버만 제거한다. indexKey 자체를 delete()하면, 이 범위조회 이후 동시에
      // 다른 요청이 추가한 세션의 인덱스 엔트리까지 같이 지워져서 좀비 세션이 생긴다.
      if (!sessionIds.isEmpty()) {
        zSetOps.remove(indexKey, sessionIds.toArray());
      }
    }
  }

  private void pruneExpired(String indexKey) {
    zSetOps.removeRangeByScore(indexKey, 0, Instant.now(clock).toEpochMilli());
  }

  private void refreshIndexExpiry(String indexKey, Instant candidateExpiresAt) {
    // 인덱스가 지금 들고 있는 세션들 중 "가장 늦게 끝나는" 만료시각을 구해서,
    // 인덱스 TTL을 그 시각과 같거나 그보다 늦게 맞춘다.
    Set<ZSetOperations.TypedTuple<String>> highest = zSetOps.reverseRangeWithScores(indexKey, 0, 0);

    Instant maxExpiresAt = candidateExpiresAt;
    if (highest != null && !highest.isEmpty()) {
      Double maxScore = highest.iterator().next().getScore();
      if (maxScore != null) {
        maxExpiresAt = Instant.ofEpochMilli(Math.max(candidateExpiresAt.toEpochMilli(), maxScore.longValue()));
      }
    }
    redisTemplate.expireAt(indexKey, maxExpiresAt);
  }

  private UserSession toUserSession(UUID userId, UUID sessionId, Map<String, String> fields) {
    UUID refreshJti = UUID.fromString(fields.get(FIELD_REFRESH_JTI));
    Instant issuedAt = Instant.parse(fields.get(FIELD_ISSUED_AT));
    return new UserSession(userId, sessionId, refreshJti, issuedAt);
  }

  private String sessionKey(UUID userId, UUID sessionId) {
    return sessionKeyPrefix(userId) + sessionId;
  }

  private String sessionKeyPrefix(UUID userId) {
    return SESSION_KEY_PREFIX + userId + ":";
  }

  private String indexKey(UUID userId) {
    return INDEX_KEY_PREFIX + userId;
  }
}
