package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.mapper.WeatherMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kakao.KakaoLocalClient;
import com.sprint.mission.otboo.external.kakao.KakaoRegionParser;
import com.sprint.mission.otboo.external.kma.KmaForecastParser;
import com.sprint.mission.otboo.external.kma.KmaWeatherClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final LocationRepository locationRepository;
  private final WeatherRepository weatherRepository;
  private final KmaWeatherClient kmaWeatherClient;
  private final KmaForecastParser kmaForecastParser;
  private final KakaoLocalClient kakaoLocalClient;
  private final KakaoRegionParser kakaoRegionParser;
  private final WeatherMapper weatherMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WeatherService(LocationRepository locationRepository, WeatherRepository weatherRepository,
      KmaWeatherClient kmaWeatherClient, KmaForecastParser kmaForecastParser,
      KakaoLocalClient kakaoLocalClient, KakaoRegionParser kakaoRegionParser,
      WeatherMapper weatherMapper, ObjectMapper objectMapper, Clock clock) {
    this.locationRepository = locationRepository;
    this.weatherRepository = weatherRepository;
    this.kmaWeatherClient = kmaWeatherClient;
    this.kmaForecastParser = kmaForecastParser;
    this.kakaoLocalClient = kakaoLocalClient;
    this.kakaoRegionParser = kakaoRegionParser;
    this.weatherMapper = weatherMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public List<WeatherDto> getWeather(double latitude, double longitude) {
    // TODO: 격자 변환은 다음 단계에서 연결
    int x = 60;
    int y = 127;

    Location location = locationRepository.findByXAndY(x, y).orElseThrow();

    LocalDate yesterday = LocalDate.now(clock.withZone(KST)).minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    List<Weather> latestRevisions = weatherRepository.findLatestRevisions(location, from);

    return latestRevisions.stream()
        .map(weatherMapper::toDto)
        .toList();
  }
}
