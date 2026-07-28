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
import org.springframework.stereotype.Component;

@Component
public class LocationResolver {

  private final WeatherGridRepository weatherGridRepository;
  private final WeatherGridWriter weatherGridWriter;
  private final LocationRepository locationRepository;
  private final LocationWriter locationWriter;
  private final KakaoRegionFetcher kakaoRegionFetcher;

  public LocationResolver(WeatherGridRepository weatherGridRepository,
      WeatherGridWriter weatherGridWriter, LocationRepository locationRepository,
      LocationWriter locationWriter, KakaoRegionFetcher kakaoRegionFetcher) {
    this.weatherGridRepository = weatherGridRepository;
    this.weatherGridWriter = weatherGridWriter;
    this.locationRepository = locationRepository;
    this.locationWriter = locationWriter;
    this.kakaoRegionFetcher = kakaoRegionFetcher;
  }

  public WeatherGrid resolveWeatherGrid(KmaGridPoint grid) {
    return weatherGridRepository.findByXAndY(grid.nx(), grid.ny())
        .orElseGet(() -> weatherGridWriter.save(grid.nx(), grid.ny()));
  }

  public List<String> resolveLocationNames(double latitude, double longitude) {
    BlockIndex blockIndex = LocationBlockCalculator.toBlock(latitude, longitude);
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