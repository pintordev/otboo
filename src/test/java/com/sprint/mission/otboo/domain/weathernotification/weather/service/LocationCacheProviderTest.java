package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.LocationCacheConfigurationContributor;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.global.config.CacheConfig;
import com.sprint.mission.otboo.global.exception.CacheErrorLoggingHandler;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = {
    DataRedisAutoConfiguration.class, JacksonAutoConfiguration.class, CacheConfig.class,
    CacheErrorLoggingHandler.class, LocationCacheConfigurationContributor.class,
    LocationCacheProvider.class
})
class LocationCacheProviderTest implements RedisTestContainerSupport {

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @Autowired
  private LocationCacheProvider locationCacheProvider;
  @MockitoBean
  private LocationRepository locationRepository;
  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @AfterEach
  void clearCache() {
    stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @Nested
  @DisplayName("지역명 캐시")
  class FindCachedLocationNames {

    @Test
    @DisplayName("같은_블록을_반복_조회하면_두_번째부터는_DB를_타지_않는다")
    void 같은_블록을_반복_조회하면_두_번째부터는_DB를_타지_않는다() {
      // given
      Location location = Location.create(37, 127, List.of("서울시", "강남구"));
      given(locationRepository.findByLatBlockAndLonBlock(37, 127))
          .willReturn(Optional.of(location));

      // when
      locationCacheProvider.findCachedLocationNames(37, 127);
      await().atMost(Duration.ofSeconds(5))
          .until(() -> !stringRedisTemplate.keys("location*").isEmpty());
      locationCacheProvider.findCachedLocationNames(37, 127);

      // then
      verify(locationRepository, times(1)).findByLatBlockAndLonBlock(37, 127);
    }

    @Test
    @DisplayName("DB에도_없으면_빈_Optional을_반환하고_캐시하지_않는다")
    void DB에도_없으면_빈_Optional을_반환하고_캐시하지_않는다() {
      // given
      given(locationRepository.findByLatBlockAndLonBlock(0, 0)).willReturn(Optional.empty());

      // when
      Optional<List<String>> result = locationCacheProvider.findCachedLocationNames(0, 0);

      // then
      assertThat(result).isEmpty();
    }
  }
}