package com.sprint.mission.otboo.security.usersession.registry.decorator;

import com.sprint.mission.otboo.security.usersession.dto.UserSession;
import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.registry.UserSessionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 순수 저장소(delegate) 앞단에서 동시 로그인 정책을 강제하는 데코레이터.
 * issue() 호출 시 정책이 delegate를 어떻게 쓸지 결정하고, 나머지는 그대로 위임한다.
 */
public class ConcurrentPolicyUserSessionRegistry implements UserSessionRegistry {

  private final UserSessionRegistry delegate;
  private final ConcurrentUserSessionPolicy policy;

  public ConcurrentPolicyUserSessionRegistry(UserSessionRegistry delegate,
      ConcurrentUserSessionPolicy policy) {
    this.delegate = delegate;
    this.policy = policy;
  }

  @Override
  public UserSessionExpirationPolicy expirationPolicy() {
    return delegate.expirationPolicy();
  }

  @Override
  public UserSession issue(UUID userId, Instant now) {
    return policy.issue(userId, now, delegate);
  }

  @Override
  public UserSession save(UserSession session, Instant expiresAt) {
    return delegate.save(session, expiresAt);
  }

  @Override
  public UserSession replaceAll(UserSession session, Instant expiresAt) {
    return delegate.replaceAll(session, expiresAt);
  }

  @Override
  public UserSession evictOldestAndSave(UserSession session, int maxDevices, Instant expiresAt) {
    return delegate.evictOldestAndSave(session, maxDevices, expiresAt);
  }

  @Override
  public UserSession compareAndRotate(UUID userId, UUID sessionId, UUID expectedRefreshJti,
      UUID newRefreshJti, Instant issuedAt, Instant expiresAt) {
    return delegate.compareAndRotate(userId, sessionId, expectedRefreshJti, newRefreshJti,
        issuedAt, expiresAt);
  }

  @Override
  public Optional<UserSession> find(UUID userId, UUID sessionId) {
    return delegate.find(userId, sessionId);
  }

  @Override
  public List<UserSession> findAllByUserId(UUID userId) {
    return delegate.findAllByUserId(userId);
  }

  @Override
  public void revoke(UUID userId, UUID sessionId) {
    delegate.revoke(userId, sessionId);
  }

  @Override
  public void revokeAll(UUID userId) {
    delegate.revokeAll(userId);
  }
}
