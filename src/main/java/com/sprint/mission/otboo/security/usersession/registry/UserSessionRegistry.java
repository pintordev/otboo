package com.sprint.mission.otboo.security.usersession.registry;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.exception.business.UserSessionExpiredException;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRegistry {

  UserSessionExpirationPolicy expirationPolicy();

  default UserSession issue(UUID userId, Instant now) {
    UserSession issued = UserSession.issue(userId, now);
    Instant expiresAt = requireFuture(expirationPolicy().expiresAtOnIssue(now), now);
    return save(issued, expiresAt);
  }

  /** 이 유저의 기존 세션을 전부 회수하고 새 세션을 원자적으로 발급한다 (단일 기기 로그인 등에서 사용). */
  default UserSession issueExclusive(UUID userId, Instant now) {
    UserSession issued = UserSession.issue(userId, now);
    Instant expiresAt = requireFuture(expirationPolicy().expiresAtOnIssue(now), now);
    return replaceAll(issued, expiresAt);
  }

  /**
   * 이 유저의 세션 수가 maxDevices 이상이면 issuedAt이 가장 오래된 세션부터 회수해 정원을
   * 맞춘 뒤, 새 세션을 원자적으로 발급한다 (최대 기기 수 제한 로그인 등에서 사용).
   */
  default UserSession evictOldestAndIssue(UUID userId, int maxDevices, Instant now) {
    UserSession issued = UserSession.issue(userId, now);
    Instant expiresAt = requireFuture(expirationPolicy().expiresAtOnIssue(now), now);
    return evictOldestAndSave(issued, maxDevices, expiresAt);
  }

  default UserSession rotate(UUID userId, UUID sessionId, UUID currentRefreshJti, Instant now) {
    UserSession current = verifyUserSession(userId, sessionId);
    Instant expiresAt = requireFuture(expirationPolicy().expiresAtOnRotate(current, now), now);
    // jti 비교와 갱신을 compareAndRotate 하나의 원자적 연산으로 묶는다.
    // find로 먼저 읽은 뒤 따로 비교/저장하면, 동시에 들어온 rotate 요청끼리 서로의 쓰기를
    // 덮어써서 방금 발급한 refreshToken이 즉시 재사용 취급되는 레이스가 생긴다.
    return compareAndRotate(userId, sessionId, currentRefreshJti, UUID.randomUUID(),
        current.issuedAt(), expiresAt);
  }

  default UserSession verifyUserSession(UUID userId, UUID sessionId) {
    // (userId, sessionId) 조합으로 조회가 안 되면, "TTL 만료"든 "다른 곳에서 로그인해서
    // 회수됨"이든 결과적으로 재로그인 대상인 건 똑같다. 예전처럼 따로 비교할 필요가 없다.
    return find(userId, sessionId)
        .orElseThrow(UserSessionExpiredException::withNone);
  }

  private static Instant requireFuture(Instant expiresAt, Instant now) {
    // Redis EXPIREAT는 과거/현재 시각이 들어오면 그 즉시 키를 삭제한다.
    // 저장소가 그걸 조용히 감추게 두지 말고, 정책이 잘못된 값을 준 시점에 바로 터지게 한다.
    if (!expiresAt.isAfter(now)) {
      throw new IllegalStateException(
          "만료 정책이 유효하지 않은 만료시각을 반환했습니다. expiresAt=" + expiresAt + ", now=" + now);
    }
    return expiresAt;
  }

  UserSession save(UserSession session, Instant expiresAt);

  /** session의 userId가 가진 기존 세션을 전부 지우고 session만 원자적으로 저장한다. */
  UserSession replaceAll(UserSession session, Instant expiresAt);

  /**
   * session의 userId가 가진 기존 세션 수가 maxDevices 이상이면 issuedAt이 가장 오래된 세션부터
   * 회수해 정원을 맞춘 뒤, session을 원자적으로 저장한다.
   */
  UserSession evictOldestAndSave(UserSession session, int maxDevices, Instant expiresAt);

  /**
   * 저장된 refreshJti가 expectedRefreshJti와 같을 때만 원자적으로 newRefreshJti로 교체한다.
   * 세션이 없으면 {@link UserSessionExpiredException}, refreshJti가 다르면(재사용 의심)
   * 이 유저의 모든 세션을 회수한 뒤 RefreshTokenReusedException을 던진다.
   */
  UserSession compareAndRotate(UUID userId, UUID sessionId, UUID expectedRefreshJti,
      UUID newRefreshJti, Instant issuedAt, Instant expiresAt);

  Optional<UserSession> find(UUID userId, UUID sessionId);

  List<UserSession> findAllByUserId(UUID userId);

  void revoke(UUID userId, UUID sessionId);

  void revokeAll(UUID userId);
}
