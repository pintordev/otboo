package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.WeatherCacheConfigurationContributor;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.global.config.CacheConfig;
import com.sprint.mission.otboo.global.exception.CacheErrorLoggingHandler;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {
    DataRedisAutoConfiguration.class, JacksonAutoConfiguration.class, CacheConfig.class,
    CacheErrorLoggingHandler.class, WeatherCacheConfigurationContributor.class,
    WeatherCacheProvider.class
})
class WeatherCacheProviderTest implements RedisTestContainerSupport {

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @Autowired
  private WeatherCacheProvider weatherCacheProvider;
  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @AfterEach
  void clearCache() {
    stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  private static WeatherGrid fixtureWeatherGrid(int x, int y) {
    return WeatherGrid.create(x, y);
  }

  private static Weather weatherWithForecastedAt(WeatherGrid weatherGrid, Instant forecastedAt) {
    return Weather.create(weatherGrid, forecastedAt, Instant.parse("2026-08-25T00:00:00Z"),
        SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 40.0, null, 25.0, null, 20.0, 28.0,
        2.0, WindStrength.WEAK, null, null, null, null);
  }

  @Nested
  @DisplayName("날씨 슬롯 캐시")
  class WeatherSlotsCache {

    @Test
    @DisplayName("캐시에_없으면_빈_리스트를_반환한다")
    void 캐시에_없으면_빈_리스트를_반환한다() {
      // given
      WeatherGrid weatherGrid = fixtureWeatherGrid(60, 127);

      // when
      List<Weather> result = weatherCacheProvider.findCachedSlots(weatherGrid);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("putSlots로_쓴_값을_findCachedSlots가_그대로_반환한다")
    void putSlots로_쓴_값을_findCachedSlots가_그대로_반환한다() {
      // given
      WeatherGrid weatherGrid = fixtureWeatherGrid(60, 127);
      Instant forecastedAt = Instant.parse("2026-08-25T11:00:00Z");
      List<Weather> slots = List.of(weatherWithForecastedAt(weatherGrid, forecastedAt));

      // when
      weatherCacheProvider.putSlots(weatherGrid, slots);
      List<Weather> result = weatherCacheProvider.findCachedSlots(weatherGrid);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getForecastedAt()).isEqualTo(forecastedAt);
    }

    @Test
    @DisplayName("putSlots를_다시_호출하면_이전_값을_덮어쓴다")
    void putSlots를_다시_호출하면_이전_값을_덮어쓴다() {
      // given - 결정 1·4: 원자적 가드 없이 그냥 덮어쓰기만으로 충분하다는 설계를 그대로 검증
      WeatherGrid weatherGrid = fixtureWeatherGrid(60, 127);
      Instant older = Instant.parse("2026-08-25T08:00:00Z");
      Instant newer = Instant.parse("2026-08-25T11:00:00Z");
      weatherCacheProvider.putSlots(weatherGrid,
          List.of(weatherWithForecastedAt(weatherGrid, older)));

      // when
      weatherCacheProvider.putSlots(weatherGrid,
          List.of(weatherWithForecastedAt(weatherGrid, newer)));

      // then
      List<Weather> result = weatherCacheProvider.findCachedSlots(weatherGrid);
      assertThat(result.get(0).getForecastedAt()).isEqualTo(newer);
    }
  }
}