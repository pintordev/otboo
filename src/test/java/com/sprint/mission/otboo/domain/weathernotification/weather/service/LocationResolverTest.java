package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.LocationBlockProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.singleflight.SingleFlightRegistry;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import com.sprint.mission.otboo.external.kakao.KakaoRegionFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

  @Mock
  private WeatherGridRepository weatherGridRepository;
  @Mock
  private WeatherGridWriter weatherGridWriter;
  @Mock
  private LocationRepository locationRepository;
  @Mock
  private LocationWriter locationWriter;
  @Mock
  private KakaoRegionFetcher kakaoRegionFetcher;
  @Mock
  private LocationCacheProvider locationCacheProvider;
  @Mock
  private SingleFlightRegistry singleFlightRegistry;

  private LocationBlockCalculator locationBlockCalculator;
  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    locationBlockCalculator = new LocationBlockCalculator(new LocationBlockProperties(500.0));
    locationResolver = new LocationResolver(weatherGridRepository, weatherGridWriter,
        locationRepository, locationWriter, kakaoRegionFetcher, locationBlockCalculator,
        locationCacheProvider, singleFlightRegistry);
  }

  @Nested
  @DisplayName("ResolveWeatherGrid")
  class ResolveWeatherGrid {

    @Test
    @DisplayName("기존_격자가_있으면_그대로_반환한다")
    void 기존_격자가_있으면_그대로_반환한다() {
      // given
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      given(weatherGridRepository.findByXAndY(60, 127)).willReturn(Optional.of(weatherGrid));

      // when
      WeatherGrid result = locationResolver.resolveWeatherGrid(grid);

      // then
      assertThat(result).isEqualTo(weatherGrid);
      verifyNoInteractions(weatherGridWriter);
    }

    @Test
    @DisplayName("없으면_WeatherGridWriter로_생성한다")
    void 없으면_WeatherGridWriter로_생성한다() {
      // given
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      WeatherGrid createdWeatherGrid = WeatherGrid.create(60, 127);
      given(weatherGridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());
      given(weatherGridWriter.save(60, 127)).willReturn(createdWeatherGrid);

      // when
      WeatherGrid result = locationResolver.resolveWeatherGrid(grid);

      // then
      assertThat(result).isEqualTo(createdWeatherGrid);
    }
  }

  @Nested
  @DisplayName("ResolveLocationNames")
  class ResolveLocationNames {

    @Test
    @DisplayName("캐시된_블록이_있으면_카카오_호출_없이_반환한다")
    void 캐시된_블록이_있으면_카카오_호출_없이_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      Location cached = Location.create(block.latBlock(), block.lonBlock(),
          List.of("서울특별시", "중구"));
      given(locationRepository.findByLatBlockAndLonBlock(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.of(cached));

      // when
      List<String> result = locationResolver.resolveLocationNames(latitude, longitude);

      // then
      assertThat(result).containsExactly("서울특별시", "중구");
      verifyNoInteractions(kakaoRegionFetcher, locationWriter);
    }

    @Test
    @DisplayName("캐시된_블록이_없으면_카카오_호출_후_LocationWriter로_캐싱한다")
    void 캐시된_블록이_없으면_카카오_호출_후_LocationWriter로_캐싱한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);

      given(locationRepository.findByLatBlockAndLonBlock(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.empty());
      given(kakaoRegionFetcher.fetch(latitude, longitude))
          .willReturn(List.of("서울특별시", "중구", "명동", ""));
      Location savedLocation = Location.create(block.latBlock(), block.lonBlock(),
          List.of("서울특별시", "중구", "명동", ""));
      given(locationWriter.save(eq(block.latBlock()), eq(block.lonBlock()),
          eq(List.of("서울특별시", "중구", "명동", "")))).willReturn(savedLocation);

      // when
      List<String> result = locationResolver.resolveLocationNames(latitude, longitude);

      // then
      assertThat(result).containsExactly("서울특별시", "중구", "명동", "");
      verify(locationWriter).save(block.latBlock(), block.lonBlock(),
          List.of("서울특별시", "중구", "명동", ""));
    }
  }

  @Nested
  @DisplayName("비동기 조회(single-flight)")
  class ResolveLocationNamesAsync {

    private final Executor directExecutor = Runnable::run;

    @Test
    @DisplayName("같은_블록_동시_호출은_콜드미스여도_SingleFlightRegistry를_한_번만_호출한다")
    void 같은_블록_동시_호출은_콜드미스여도_SingleFlightRegistry를_한_번만_호출한다() throws Exception {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      given(locationCacheProvider.findCachedLocationNames(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.empty());
      CountDownLatch executeStarted = new CountDownLatch(1);
      CountDownLatch releaseExecute = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      given(singleFlightRegistry.execute(anyString(), any(), any(), any())).willAnswer(
          invocation -> {
            executeStarted.countDown();
            return CompletableFuture.supplyAsync(() -> {
              try {
                releaseExecute.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.<String>of();
            }, pool);
          });

      try {
        // when - 두 스레드가 동시에 같은 블록으로 resolveLocationNamesAsync 호출
        Future<?> first = pool.submit(() ->
            locationResolver.resolveLocationNamesAsync(latitude, longitude, pool));
        assertThat(executeStarted.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<List<String>> second =
            locationResolver.resolveLocationNamesAsync(latitude, longitude, pool);
        releaseExecute.countDown();
        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);

        // then
        verify(singleFlightRegistry, times(1)).execute(anyString(), any(), any(), any());
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    @DisplayName("로컬_in_flight에_없으면_SingleFlightRegistry로_위임한다")
    void 로컬_in_flight에_없으면_SingleFlightRegistry로_위임한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      given(locationCacheProvider.findCachedLocationNames(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.empty());
      given(singleFlightRegistry.execute(anyString(), any(), any(), any()))
          .willReturn(CompletableFuture.completedFuture(List.of()));

      // when
      locationResolver.resolveLocationNamesAsync(latitude, longitude, directExecutor);

      // then - 락 키는 블록 좌표 기준(로컬 in-flight의 BlockIndex 키와 동일 granularity)
      verify(singleFlightRegistry).execute(
          eq("location:" + block.latBlock() + ":" + block.lonBlock()),
          any(), eq(directExecutor), any());
    }

    @Test
    @DisplayName("SingleFlightRegistry가_이미_완료된_future를_반환해도_정상적으로_결과를_반환한다")
    void SingleFlightRegistry가_이미_완료된_future를_반환해도_정상적으로_결과를_반환한다()
        throws Exception {
      // given - reload가 이미 값을 가진 상태라 execute가 즉시 완료된 future를 반환하는 상황.
      // 이때 whenComplete가 computeIfAbsent 밖에서 실행돼야 예외 없이 정상 완료된다.
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = locationBlockCalculator.toBlock(latitude, longitude);
      given(locationCacheProvider.findCachedLocationNames(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.empty());
      given(singleFlightRegistry.execute(anyString(), any(), any(), any()))
          .willReturn(CompletableFuture.completedFuture(List.of("서울특별시", "중구")));

      // when
      CompletableFuture<List<String>> result =
          locationResolver.resolveLocationNamesAsync(latitude, longitude, directExecutor);

      // then
      assertThat(result.get(1, TimeUnit.SECONDS)).containsExactly("서울특별시", "중구");
    }
  }
}