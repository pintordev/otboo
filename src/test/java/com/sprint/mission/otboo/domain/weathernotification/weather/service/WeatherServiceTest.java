package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.mapper.WeatherMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kakao.KakaoLocalClient;
import com.sprint.mission.otboo.external.kakao.KakaoRegionParser;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
import com.sprint.mission.otboo.external.kma.KmaForecastParser;
import com.sprint.mission.otboo.external.kma.KmaWeatherClient;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

  @Mock
  private LocationRepository locationRepository;
  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private KmaWeatherClient kmaWeatherClient;
  @Mock
  private KmaForecastParser kmaForecastParser;
  @Mock
  private KakaoLocalClient kakaoLocalClient;
  @Mock
  private KakaoRegionParser kakaoRegionParser;
  @Mock
  private WeatherMapper weatherMapper;

  private WeatherService weatherService;

  @BeforeEach
  void setUp() {
    // 2026-07-27 18:00 KST 고정 - 17시 발표가 최신
    Clock clock = Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC);
    weatherService = new WeatherService(locationRepository, weatherRepository, kmaWeatherClient,
        kmaForecastParser, kakaoLocalClient, kakaoRegionParser, weatherMapper,
        new ObjectMapper(), clock, "kma-service-key", "kakao-rest-api-key");
  }

  @Nested
  @DisplayName("GetWeather")
  class GetWeather {

    @Test
    @DisplayName("DB에_최신_데이터가_있으면_라이브_호출_없이_반환한다")
    void DB에_최신_데이터가_있으면_라이브_호출_없이_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      Location location = Location.create(latitude, longitude, 60, 127, List.of("서울특별시"));
      given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.of(location));

      Instant freshForecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Weather todayWeather = Weather.create(location, freshForecastedAt,
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE,
          0.0, 10.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.0, WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(location), any()))
          .willReturn(List.of(todayWeather));

      WeatherDto expectedDto = new WeatherDto(todayWeather.getId(), freshForecastedAt,
          todayWeather.getForecastAt(), null, SkyStatus.CLEAR, null, null, null, null);
      given(weatherMapper.toDto(todayWeather)).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).containsExactly(expectedDto);
      verifyNoInteractions(kmaWeatherClient, kakaoLocalClient);
    }

    @Test
    @DisplayName("신규_위치는_라이브로_재조회해서_저장한_후_반환한다")
    void 신규_위치는_라이브로_재조회해서_저장한_후_반환한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;

      given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

      KakaoRegionResponse kakaoResponse = new KakaoRegionResponse(List.of());
      given(kakaoLocalClient.getRegionCode("KakaoAK kakao-rest-api-key", longitude, latitude))
          .willReturn(kakaoResponse);
      given(kakaoRegionParser.toLocationNames(kakaoResponse))
          .willReturn(List.of("서울특별시", "중구", "명동", ""));

      Location createdLocation = Location.create(latitude, longitude, 60, 127,
          List.of("서울특별시", "중구", "명동", ""));
      given(locationRepository.findByXAndY(60, 127))
          .willReturn(Optional.empty(), Optional.of(createdLocation));

      given(weatherRepository.findLatestRevisions(eq(createdLocation), any()))
          .willReturn(List.of());

      KmaWeatherResponse kmaResponse = new KmaWeatherResponse(null);
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 1000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(kmaResponse);

      DailyWeatherForecastDto todayForecast = new DailyWeatherForecastDto(
          LocalDate.of(2026, 7, 27), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 10.0, 65.0,
          28.0, 25.0, 31.0, 2.0);
      given(kmaForecastParser.parseDailyForecast(eq(kmaResponse), any()))
          .willReturn(List.of(todayForecast));

      given(weatherRepository.save(any(Weather.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      WeatherDto expectedDto = new WeatherDto(null, null, null, null, SkyStatus.CLEAR, null, null,
          null, null);
      given(weatherMapper.toDto(any(Weather.class))).willReturn(expectedDto);

      // when
      List<WeatherDto> result = weatherService.getWeather(latitude, longitude);

      // then
      assertThat(result).containsExactly(expectedDto);
      verify(locationRepository).insertIfAbsent(any(), eq(60), eq(127), eq(latitude),
          eq(longitude), any());
      verify(weatherRepository).save(any(Weather.class));
    }

    @Test
    @DisplayName("기존_위치의_오늘_데이터가_stale하면_전날_데이터로_diff를_계산해서_재조회한다")
    void 기존_위치의_오늘_데이터가_stale하면_전날_데이터로_diff를_계산해서_재조회한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      Location location = Location.create(latitude, longitude, 60, 127, List.of("서울특별시"));
      given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.of(location));

      // 어제(D-1) 데이터만 존재, 오늘 데이터는 없음(stale)
      Weather yesterdayWeather = Weather.create(location, Instant.parse("2026-07-26T08:00:00Z"),
          Instant.parse("2026-07-26T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 60.0, 0.0, 26.0, 0.0, 24.0, 29.0, 2.0, WindStrength.WEAK);
      given(weatherRepository.findLatestRevisions(eq(location), any()))
          .willReturn(List.of(yesterdayWeather));

      KmaWeatherResponse kmaResponse = new KmaWeatherResponse(null);
      given(kmaWeatherClient.getVillageForecast("kma-service-key", 1000, 1, "JSON", "20260727",
          "1700", 60, 127)).willReturn(kmaResponse);

      DailyWeatherForecastDto todayForecast = new DailyWeatherForecastDto(
          LocalDate.of(2026, 7, 27), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 10.0, 65.0,
          28.0, 25.0, 31.0, 2.0);
      given(kmaForecastParser.parseDailyForecast(eq(kmaResponse), any()))
          .willReturn(List.of(todayForecast));

      given(weatherRepository.save(any(Weather.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      given(weatherMapper.toDto(any(Weather.class))).willAnswer(
          invocation -> new WeatherDto(null, null, null, null,
              ((Weather) invocation.getArgument(0)).getSkyStatus(), null, null, null, null));

      // when
      weatherService.getWeather(latitude, longitude);

      // then
      org.mockito.ArgumentCaptor<Weather> captor = org.mockito.ArgumentCaptor.forClass(
          Weather.class);
      verify(weatherRepository).save(captor.capture());
      Weather savedTodayWeather = captor.getValue();
      assertThat(savedTodayWeather.getTemperatureCompared()).isEqualTo(2.0); // 28.0 - 26.0
      assertThat(savedTodayWeather.getHumidityCompared()).isEqualTo(5.0); // 65.0 - 60.0
      verifyNoInteractions(kakaoLocalClient);
    }
  }
}
