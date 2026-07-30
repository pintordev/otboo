package com.sprint.mission.otboo.global.usersession;

import com.sprint.mission.otboo.global.usersession.exception.RefreshTokenReusedException;
import com.sprint.mission.otboo.global.usersession.exception.UserSessionExpiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSessionRegistry {

  private static final String KEY_PREFIX = "auth:user-session:";
  private static final String FIELD_SESSION_ID = "sessionId";
  private static final String FIELD_REFRESH_JTI = "refreshJti";
  private static final String FIELD_ISSUED_AT = "issuedAt";

  private final StringRedisTemplate redisTemplate;

  // 세션 발급만
  public UserSession issue() {
    return new UserSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
  }

  // RTR 발급만
  public UserSession rotate(UUID userId, UUID sessionId, UUID currentRefreshJti) {

    UserSession current = find(userId)
        .orElseThrow(UserSessionExpiredException::withNone);

    if (!current.sessionId().equals(sessionId)) {
      throw UserSessionExpiredException.withNone();
    }

    if (!current.currentRefreshJti().equals(currentRefreshJti)) {
      revoke(userId);
      throw RefreshTokenReusedException.withNone();
    }

    // 절대 만료 정책: 팀원들간 논의 필요
    /**
     * 주의사항: AccessToken의 만료 시간을 이제 now로 직접 생성해야함(여기서 반환되는 issuedAt을 사용하면 절대 안됨)
     */
    return new UserSession(sessionId, UUID.randomUUID(), current.issuedAt());
  }

  // AccessToken 세션 유효성 검증
  public UserSession verifyLoginSession(UUID userId, UUID sessionId) {

    UserSession current = find(userId)
        .orElseThrow(UserSessionExpiredException::withNone);

    if (!current.sessionId().equals(sessionId)) {
      throw UserSessionExpiredException.withNone();
    }

    return current;
  }

  // 세션 실제 저장
  public UserSession save(UUID userId, UserSession session, Instant refreshTokenExpiresAt) {

    String redisKey = key(userId);

    Map<String, String> fields = Map.of(
        FIELD_SESSION_ID, session.sessionId().toString(),
        FIELD_REFRESH_JTI, session.currentRefreshJti().toString(),
        FIELD_ISSUED_AT, session.issuedAt().toString()
    );

    // Redis의 원자적 쓰기 보장
    redisTemplate.execute(new SessionCallback<Object>() {
      @Override
      public Object execute(RedisOperations operations) {
        operations.multi();
        operations.opsForHash().putAll(redisKey, fields);
        operations.expireAt(redisKey, refreshTokenExpiresAt);
        return operations.exec();
      }
    });

    return session;
  }

  public void revoke(UUID userId) {
    redisTemplate.delete(key(userId));
  }

  public Optional<UserSession> find(UUID userId) {

    Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(userId));
    if (entries.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new UserSession(
        UUID.fromString((String) entries.get(FIELD_SESSION_ID)),
        UUID.fromString((String) entries.get(FIELD_REFRESH_JTI)),
        Instant.parse((String) entries.get(FIELD_ISSUED_AT))
    ));
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
