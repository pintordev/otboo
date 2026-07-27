package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.sprint.mission.otboo.external.kma.KmaForecastParser;
import com.sprint.mission.otboo.external.kma.KmaWeatherClient;
import java.time.Clock;
import java.time.Instant;
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
        new ObjectMapper(), clock);
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
  }
}
