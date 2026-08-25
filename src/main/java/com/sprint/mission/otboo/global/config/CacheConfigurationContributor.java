package com.sprint.mission.otboo.global.config;

import org.springframework.data.redis.cache.RedisCacheConfiguration;

public interface CacheConfigurationContributor {

  String cacheName();

  RedisCacheConfiguration cacheConfiguration();
}