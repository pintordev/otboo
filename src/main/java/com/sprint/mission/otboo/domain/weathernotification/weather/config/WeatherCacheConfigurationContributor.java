package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
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
public class WeatherCacheConfigurationContributor implements CacheConfigurationContributor {

  private final WeatherCacheProperties weatherCacheProperties;
  private final ObjectMapper objectMapper;

  @Override
  public String cacheName() {
    return "weather";
  }

  @Override
  public RedisCacheConfiguration cacheConfiguration() {
    JavaType listOfWeather = objectMapper.getTypeFactory()
        .constructCollectionType(List.class, Weather.class);
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(weatherCacheProperties.ttlHours()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, listOfWeather)));
  }
}