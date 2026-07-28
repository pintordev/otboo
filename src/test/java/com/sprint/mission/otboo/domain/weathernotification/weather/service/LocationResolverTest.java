package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import com.sprint.mission.otboo.external.kakao.KakaoRegionFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
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

  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    locationResolver = new LocationResolver(weatherGridRepository, weatherGridWriter,
        locationRepository, locationWriter, kakaoRegionFetcher);
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
      BlockIndex block = LocationBlockCalculator.toBlock(latitude, longitude);
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
      BlockIndex block = LocationBlockCalculator.toBlock(latitude, longitude);

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
}