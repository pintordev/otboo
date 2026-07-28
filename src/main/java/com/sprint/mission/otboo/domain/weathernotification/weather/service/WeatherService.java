package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.exception.InvalidCoordinateException;
import com.sprint.mission.otboo.domain.weathernotification.weather.mapper.WeatherMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kakao.KakaoLocalClient;
import com.sprint.mission.otboo.external.kakao.KakaoRegionParser;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastParser;
import com.sprint.mission.otboo.external.kma.KmaGridConverter;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.KmaWeatherClient;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
public class WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int FORECAST_NUM_OF_ROWS = 1000;
  private static final int FORECAST_PAGE_NO = 1;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final LocationRepository locationRepository;
  private final WeatherRepository weatherRepository;
  private final KmaWeatherClient kmaWeatherClient;
  private final KmaForecastParser kmaForecastParser;
  private final KakaoLocalClient kakaoLocalClient;
  private final KakaoRegionParser kakaoRegionParser;
  private final WeatherMapper weatherMapper;
  private final Clock clock;
  private final String kmaServiceKey;
  private final String kakaoRestApiKey;

  public WeatherService(LocationRepository locationRepository, WeatherRepository weatherRepository,
      KmaWeatherClient kmaWeatherClient, KmaForecastParser kmaForecastParser,
      KakaoLocalClient kakaoLocalClient, KakaoRegionParser kakaoRegionParser,
      WeatherMapper weatherMapper, Clock clock,
      @Value("${weather.kma.service-key}") String kmaServiceKey,
      @Value("${weather.kakao.rest-api-key}") String kakaoRestApiKey) {
    this.locationRepository = locationRepository;
    this.weatherRepository = weatherRepository;
    this.kmaWeatherClient = kmaWeatherClient;
    this.kmaForecastParser = kmaForecastParser;
    this.kakaoLocalClient = kakaoLocalClient;
    this.kakaoRegionParser = kakaoRegionParser;
    this.weatherMapper = weatherMapper;
    this.clock = clock;
    this.kmaServiceKey = kmaServiceKey;
    this.kakaoRestApiKey = kakaoRestApiKey;
  }

  @Transactional
  public List<WeatherDto> getWeather(double latitude, double longitude) {
    log.debug("날씨 조회 요청: latitude={}, longitude={}", latitude, longitude);
    KmaGridPoint grid = toGrid(latitude, longitude);
    Location location = findOrCreateLocation(grid, latitude, longitude);

    LocalDate today = LocalDate.now(clock.withZone(KST));
    LocalDate yesterday = today.minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    List<Weather> latestRevisions = weatherRepository.findLatestRevisions(location, from);

    Map<LocalDate, Weather> existingByDate = latestRevisions.stream()
        .collect(Collectors.toMap(this::toForecastDate, w -> w));

    BaseTime latestBaseTime = KmaBaseTimeCalculator.calculate(clock.instant());
    Weather todayWeather = existingByDate.get(today);
    boolean stale = todayWeather == null
        || todayWeather.getForecastedAt().isBefore(latestBaseTime.toInstant());

    List<Weather> result = stale
        ? fetchLiveAndSave(location, grid, latestBaseTime, existingByDate)
        : latestRevisions.stream()
            .filter(w -> !toForecastDate(w).isBefore(today))
            .toList();

    return result.stream().map(weatherMapper::toDto).toList();
  }

  private LocalDate toForecastDate(Weather weather) {
    return weather.getForecastAt().atZone(KST).toLocalDate();
  }

  private KmaGridPoint toGrid(double latitude, double longitude) {
    try {
      return KmaGridConverter.toGrid(latitude, longitude);
    } catch (IllegalArgumentException e) {
      log.warn("한반도 범위를 벗어난 좌표 요청: latitude={}, longitude={}", latitude, longitude);
      throw InvalidCoordinateException.of(latitude, longitude);
    }
  }

  private Location findOrCreateLocation(KmaGridPoint grid, double latitude, double longitude) {
    return locationRepository.findByXAndY(grid.nx(), grid.ny())
        .orElseGet(() -> {
          KakaoRegionResponse kakaoResponse = kakaoLocalClient.getRegionCode(
              "KakaoAK " + kakaoRestApiKey, longitude, latitude);
          List<String> locationNames = kakaoRegionParser.toLocationNames(kakaoResponse);

          locationRepository.insertIfAbsent(UUID.randomUUID(), grid.nx(), grid.ny(), latitude,
              longitude, toJson(locationNames));
          return locationRepository.findByXAndY(grid.nx(), grid.ny()).orElseThrow();
        });
  }

  private List<Weather> fetchLiveAndSave(Location location, KmaGridPoint grid,
      BaseTime baseTime, Map<LocalDate, Weather> existingByDate) {
    log.info("기상청 라이브 재조회: nx={}, ny={}, baseDate={}, baseTime={}", grid.nx(), grid.ny(),
        baseTime.baseDate(), baseTime.baseTime());
    KmaWeatherResponse response = kmaWeatherClient.getVillageForecast(kmaServiceKey,
        FORECAST_NUM_OF_ROWS, FORECAST_PAGE_NO, "JSON", baseTime.baseDate(), baseTime.baseTime(),
        grid.nx(), grid.ny());
    List<DailyWeatherForecastDto> dailyForecasts = kmaForecastParser
        .parseDailyForecast(response, clock.instant()).stream()
        .sorted(Comparator.comparing(DailyWeatherForecastDto::date))
        .toList();

    Weather previousDayWeather = dailyForecasts.isEmpty() ? null
        : existingByDate.get(dailyForecasts.get(0).date().minusDays(1));
    Double previousTemp =
        previousDayWeather != null ? previousDayWeather.getTemperatureCurrent() : null;
    Double previousHumidity =
        previousDayWeather != null ? previousDayWeather.getHumidityCurrent() : null;

    List<Weather> saved = new ArrayList<>();
    for (DailyWeatherForecastDto dto : dailyForecasts) {
      double temperatureCompared =
          previousTemp != null ? dto.temperatureCurrent() - previousTemp : 0.0;
      double humidityCompared =
          previousHumidity != null ? dto.humidityCurrent() - previousHumidity : 0.0;

      Weather weather = Weather.create(location, baseTime.toInstant(),
          dto.date().atStartOfDay(KST).toInstant(), dto.skyStatus(), dto.precipitationType(),
          dto.precipitationAmount(), dto.precipitationProbability(), dto.humidityCurrent(),
          humidityCompared, dto.temperatureCurrent(), temperatureCompared, dto.temperatureMin(),
          dto.temperatureMax(), dto.windSpeed(), toWindStrength(dto.windSpeed()));

      saved.add(weatherRepository.save(weather));

      previousTemp = dto.temperatureCurrent();
      previousHumidity = dto.humidityCurrent();
    }
    log.info("기상청 라이브 재조회 저장 완료: nx={}, ny={}, 저장 건수={}", grid.nx(), grid.ny(), saved.size());
    return saved;
  }

  private WindStrength toWindStrength(double speed) {
    if (speed < 4.0) {
      return WindStrength.WEAK;
    }
    if (speed < 9.0) {
      return WindStrength.MODERATE;
    }
    return WindStrength.STRONG;
  }

  private String toJson(List<String> locationNames) {
    try {
      return OBJECT_MAPPER.writeValueAsString(locationNames);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("locationNames 직렬화 실패", e);
    }
  }
}