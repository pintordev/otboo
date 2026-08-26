package com.sprint.mission.otboo.domain.weathernotification.weather.service;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LocationResolver {

  private final WeatherGridRepository weatherGridRepository;
  private final WeatherGridWriter weatherGridWriter;
  private final LocationRepository locationRepository;
  private final LocationWriter locationWriter;
  private final KakaoRegionFetcher kakaoRegionFetcher;
  private final LocationBlockCalculator locationBlockCalculator;
  private final LocationCacheProvider locationCacheProvider;
  private final SingleFlightRegistry singleFlightRegistry;

  private final ConcurrentHashMap<BlockIndex, CompletableFuture<List<String>>> inFlight =
      new ConcurrentHashMap<>();

  public WeatherGrid resolveWeatherGrid(KmaGridPoint grid) {
    return weatherGridRepository.findByXAndY(grid.nx(), grid.ny())
        .orElseGet(() -> weatherGridWriter.save(grid.nx(), grid.ny()));
  }

  // 로컬 in-flight 다음에 SingleFlightRegistry(분산 락)까지 태우는 2단 방어.
  public CompletableFuture<List<String>> resolveLocationNamesAsync(double latitude,
      double longitude, Executor executor) {
    BlockIndex blockIndex = locationBlockCalculator.toBlock(latitude, longitude);
    String lockKey = "location:" + blockIndex.latBlock() + ":" + blockIndex.lonBlock();
    return locationCacheProvider
        .findCachedLocationNames(blockIndex.latBlock(), blockIndex.lonBlock())
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> {
          CompletableFuture<List<String>> future = inFlight.computeIfAbsent(blockIndex, k ->
              singleFlightRegistry.execute(
                  lockKey,
                  () -> fetchAndSave(blockIndex, latitude, longitude),
                  executor,
                  () -> locationCacheProvider.findCachedLocationNames(
                      blockIndex.latBlock(), blockIndex.lonBlock())));
          // singleFlightRegistry.execute가 이미 완료된 future를 반환하면 whenComplete가
          // 즉시(동기) 실행되므로, computeIfAbsent 밖에서 맵을 수정해야 재귀 수정을 피할 수 있다
          future.whenComplete((r, e) -> inFlight.remove(blockIndex, future));
          return future;
        });
  }

  private List<String> fetchAndSave(BlockIndex blockIndex, double latitude, double longitude) {
    List<String> locationNames = kakaoRegionFetcher.fetch(latitude, longitude);
    return locationWriter.save(blockIndex.latBlock(), blockIndex.lonBlock(), locationNames)
        .getLocationNames();
  }

  public List<String> resolveLocationNames(double latitude, double longitude) {
    BlockIndex blockIndex = locationBlockCalculator.toBlock(latitude, longitude);
    return locationRepository
        .findByLatBlockAndLonBlock(blockIndex.latBlock(), blockIndex.lonBlock())
        .map(Location::getLocationNames)
        .orElseGet(() -> fetchAndSave(blockIndex, latitude, longitude));
  }
}