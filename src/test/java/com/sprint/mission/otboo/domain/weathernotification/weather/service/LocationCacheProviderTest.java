package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
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
import org.junit.jupiter.api.BeforeEach;
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

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

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

  @BeforeEach
  void setUpCache() {
    // 이전 테스트가 예외로 중단돼도 상태가 남지 않도록 시작 시점에도 비워 둔다
    stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

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
      locationCacheProvider.findCachedLocationNames(0, 0);

      // then
      assertThat(result).isEmpty();
      // 빈 결과는 캐시되지 않아야 하므로 두 번 모두 DB를 조회한다
      verify(locationRepository, times(2)).findByLatBlockAndLonBlock(0, 0);
    }

    @Test
    @DisplayName("DB에_있어도_지역명_목록이_비어있으면_캐시하지_않는다")
    void DB에_있어도_지역명_목록이_비어있으면_캐시하지_않는다() {
      // given - null 분기가 아니라 unless의 isEmpty() 분기를 직접 검증
      Location location = ENTITY_FIXTURE_MONKEY.giveMeBuilder(Location.class)
          .set("latBlock", 1)
          .set("lonBlock", 1)
          .set("locationNames", List.<String>of())
          .sample();
      given(locationRepository.findByLatBlockAndLonBlock(1, 1)).willReturn(Optional.of(location));

      // when
      Optional<List<String>> result = locationCacheProvider.findCachedLocationNames(1, 1);
      locationCacheProvider.findCachedLocationNames(1, 1);

      // then
      assertThat(result).contains(List.of());
      // 빈 리스트는 캐시되지 않아야 하므로 두 번 모두 DB를 조회한다
      verify(locationRepository, times(2)).findByLatBlockAndLonBlock(1, 1);
    }
  }
}