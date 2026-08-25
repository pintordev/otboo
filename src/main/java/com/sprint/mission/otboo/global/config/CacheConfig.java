package com.sprint.mission.otboo.global.config;

import com.sprint.mission.otboo.global.exception.CacheErrorLoggingHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

  private final CacheErrorLoggingHandler cacheErrorLoggingHandler;

  @Bean
  public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper, List<CacheConfigurationContributor> contributors) {
    RedisCacheConfiguration defaultConfig = baseConfig(objectMapper);

    Map<String, RedisCacheConfiguration> perCacheConfig = contributors.stream()
        .collect(Collectors.toMap(CacheConfigurationContributor::cacheName,
            CacheConfigurationContributor::cacheConfiguration));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(perCacheConfig)
        .build();
  }

  private RedisCacheConfiguration baseConfig(ObjectMapper objectMapper) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericJacksonJsonRedisSerializer(objectMapper)));
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return cacheErrorLoggingHandler;
  }
}