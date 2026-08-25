package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
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

  private final ConcurrentHashMap<BlockIndex, CompletableFuture<List<String>>> inFlight =
      new ConcurrentHashMap<>();

  public WeatherGrid resolveWeatherGrid(KmaGridPoint grid) {
    return weatherGridRepository.findByXAndY(grid.nx(), grid.ny())
        .orElseGet(() -> weatherGridWriter.save(grid.nx(), grid.ny()));
  }

  // 로컬 in-flight single-flight - 이후 SingleFlightRegistry(분산 락)가 한 겹 더 붙는다.
  public CompletableFuture<List<String>> resolveLocationNamesAsync(double latitude,
      double longitude, Executor executor) {
    BlockIndex blockIndex = locationBlockCalculator.toBlock(latitude, longitude);
    return locationCacheProvider
        .findCachedLocationNames(blockIndex.latBlock(), blockIndex.lonBlock())
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> inFlight.computeIfAbsent(blockIndex, k ->
            CompletableFuture.supplyAsync(() -> fetchAndSave(blockIndex, latitude, longitude),
                executor).whenComplete((r, e) -> inFlight.remove(k))));
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
        .orElseGet(() -> {
          List<String> locationNames = kakaoRegionFetcher.fetch(latitude, longitude);
          return locationWriter.save(blockIndex.latBlock(), blockIndex.lonBlock(), locationNames)
              .getLocationNames();
        });
  }
}