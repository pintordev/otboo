package com.sprint.mission.otboo.global.exception;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

// 캐시는 어디까지나 DB 앞의 보조 저장소 - Redis 장애가 원래 Redis 없이도 되던 조회까지
// 같이 죽이지 않도록 get/put/evict/clear 실패를 전부 캐시 미스로 흡수하고 로그만 남긴다.
@Slf4j
@Component
public class CacheErrorLoggingHandler implements CacheErrorHandler {

  @Override
  public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
    // location 캐시 키는 좌표(latBlock:lonBlock)에서 유도된 값이라 그대로 로깅하지 않는다
    log.error("캐시 조회 실패, 캐시 미스로 처리: cache={}, keyHash={}", cache.getName(),
        Objects.hashCode(key), exception);
  }

  @Override
  public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
      Object value) {
    log.error("캐시 저장 실패: cache={}, keyHash={}", cache.getName(), Objects.hashCode(key),
        exception);
  }

  @Override
  public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
    log.error("캐시 삭제 실패: cache={}, keyHash={}", cache.getName(), Objects.hashCode(key),
        exception);
  }

  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    log.error("캐시 전체 삭제 실패: cache={}", cache.getName(), exception);
  }
}