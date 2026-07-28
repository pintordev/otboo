package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import com.sprint.mission.otboo.external.kakao.KakaoLocalClient;
import com.sprint.mission.otboo.external.kakao.KakaoRegionParser;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocationResolver {

  private final ObjectMapper mapper = new ObjectMapper();
  private final WeatherGridRepository weatherGridRepository;
  private final LocationRepository locationRepository;
  private final KakaoLocalClient kakaoLocalClient;
  private final KakaoRegionParser kakaoRegionParser;
  private final String kakaoRestApiKey;

  public LocationResolver(WeatherGridRepository weatherGridRepository,
      LocationRepository locationRepository, KakaoLocalClient kakaoLocalClient,
      KakaoRegionParser kakaoRegionParser,
      @Value("${weather.kakao.rest-api-key}") String kakaoRestApiKey) {
    this.weatherGridRepository = weatherGridRepository;
    this.locationRepository = locationRepository;
    this.kakaoLocalClient = kakaoLocalClient;
    this.kakaoRegionParser = kakaoRegionParser;
    this.kakaoRestApiKey = kakaoRestApiKey;
  }

  public WeatherGrid resolveWeatherGrid(KmaGridPoint grid) {
    return weatherGridRepository.findByXAndY(grid.nx(), grid.ny())
        .orElseGet(() -> {
          weatherGridRepository.insertIfAbsent(UUID.randomUUID(), grid.nx(), grid.ny());
          return weatherGridRepository.findByXAndY(grid.nx(), grid.ny()).orElseThrow();
        });
  }

  public List<String> resolveLocationNames(double latitude, double longitude) {
    BlockIndex blockIndex = LocationBlockCalculator.toBlock(latitude, longitude);
    return locationRepository
        .findByLatBlockAndLonBlock(blockIndex.latBlock(), blockIndex.lonBlock())
        .map(Location::getLocationNames)
        .orElseGet(() -> fetchAndCacheLocationNames(blockIndex, latitude, longitude));
  }

  private List<String> fetchAndCacheLocationNames(BlockIndex blockIndex, double latitude,
      double longitude) {
    KakaoRegionResponse response = kakaoLocalClient.getRegionCode("KakaoAK " + kakaoRestApiKey,
        longitude, latitude);
    List<String> locationNames = kakaoRegionParser.toLocationNames(response);

    locationRepository.insertIfAbsent(UUID.randomUUID(), blockIndex.latBlock(),
        blockIndex.lonBlock(), toJson(locationNames));
    return locationRepository
        .findByLatBlockAndLonBlock(blockIndex.latBlock(), blockIndex.lonBlock())
        .map(Location::getLocationNames)
        .orElseThrow();
  }

  private String toJson(List<String> locationNames) {
    try {
      return mapper.writeValueAsString(locationNames);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("locationNames 직렬화 실패", e);
    }
  }
}