package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.global.config.CacheConfigurationContributor;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(WeatherCacheProperties.class)
@RequiredArgsConstructor
public class LocationCacheConfigurationContributor implements CacheConfigurationContributor {

  private final WeatherCacheProperties weatherCacheProperties;
  private final ObjectMapper objectMapper;

  @Override
  public String cacheName() {
    return "location";
  }

  @Override
  public RedisCacheConfiguration cacheConfiguration() {
    // Spring Cache가 Optional 반환 메서드는 저장/조회 시 자동으로 언랩/래핑하므로, 직렬화기가
    // 실제로 다루는 값은 Optional<List<String>>이 아니라 List<String>이다.
    JavaType listOfString = objectMapper.getTypeFactory()
        .constructCollectionType(List.class, String.class);
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofDays(weatherCacheProperties.locationTtlDays()))
        .disableCachingNullValues()
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, listOfString)));
  }
}
