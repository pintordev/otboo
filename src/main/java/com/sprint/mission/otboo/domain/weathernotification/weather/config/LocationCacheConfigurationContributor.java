package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.global.config.CacheConfigurationContributor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    JavaType listOfString = objectMapper.getTypeFactory()
        .constructCollectionType(List.class, String.class);
    JavaType optionalOfListOfString = objectMapper.getTypeFactory()
        .constructParametricType(Optional.class, listOfString);
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofDays(weatherCacheProperties.locationTtlDays()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, optionalOfListOfString)));
  }
}