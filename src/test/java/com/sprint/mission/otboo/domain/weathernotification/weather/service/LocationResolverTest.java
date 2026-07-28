package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.LocationBlock;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationBlockRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator;
import com.sprint.mission.otboo.domain.weathernotification.weather.util.LocationBlockCalculator.BlockIndex;
import com.sprint.mission.otboo.external.kakao.KakaoLocalClient;
import com.sprint.mission.otboo.external.kakao.KakaoRegionParser;
import com.sprint.mission.otboo.external.kakao.dto.KakaoRegionResponse;
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
  private LocationRepository locationRepository;
  @Mock
  private LocationBlockRepository locationBlockRepository;
  @Mock
  private KakaoLocalClient kakaoLocalClient;
  @Mock
  private KakaoRegionParser kakaoRegionParser;

  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    locationResolver = new LocationResolver(locationRepository, locationBlockRepository,
        kakaoLocalClient, kakaoRegionParser, "kakao-rest-api-key");
  }

  @Nested
  @DisplayName("ResolveLocation")
  class ResolveLocation {

    @Test
    @DisplayName("기존_위치가_있으면_그대로_반환한다")
    void 기존_위치가_있으면_그대로_반환한다() {
      // given
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      Location location = Location.create(37.5674783, 126.9884121, 60, 127, null);
      given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.of(location));

      // when
      Location result = locationResolver.resolveLocation(grid, 37.5674783, 126.9884121);

      // then
      assertThat(result).isEqualTo(location);
      verify(locationRepository, never()).insertIfAbsent(any(), anyInt(), anyInt(), anyDouble(),
          anyDouble());
    }

    @Test
    @DisplayName("없으면_생성_후_반환한다")
    void 없으면_생성_후_반환한다() {
      // given
      KmaGridPoint grid = new KmaGridPoint(60, 127);
      Location createdLocation = Location.create(37.5674783, 126.9884121, 60, 127, null);
      given(locationRepository.findByXAndY(60, 127))
          .willReturn(Optional.empty(), Optional.of(createdLocation));

      // when
      Location result = locationResolver.resolveLocation(grid, 37.5674783, 126.9884121);

      // then
      assertThat(result).isEqualTo(createdLocation);
      verify(locationRepository).insertIfAbsent(any(), eq(60), eq(127), eq(37.5674783),
          eq(126.9884121));
      verifyNoInteractions(kakaoLocalClient);
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
      LocationBlock cached = LocationBlock.create(block.latBlock(), block.lonBlock(),
          List.of("서울특별시", "중구"));
      given(locationBlockRepository.findByLatBlockAndLonBlock(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.of(cached));

      // when
      List<String> result = locationResolver.resolveLocationNames(latitude, longitude);

      // then
      assertThat(result).containsExactly("서울특별시", "중구");
      verifyNoInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("캐시된_블록이_없으면_카카오_호출_후_캐싱한다")
    void 캐시된_블록이_없으면_카카오_호출_후_캐싱한다() {
      // given
      double latitude = 37.5674783;
      double longitude = 126.9884121;
      BlockIndex block = LocationBlockCalculator.toBlock(latitude, longitude);

      KakaoRegionResponse kakaoResponse = new KakaoRegionResponse(List.of());
      LocationBlock savedBlock = LocationBlock.create(block.latBlock(), block.lonBlock(),
          List.of("서울특별시", "중구", "명동", ""));
      given(locationBlockRepository.findByLatBlockAndLonBlock(block.latBlock(), block.lonBlock()))
          .willReturn(Optional.empty(), Optional.of(savedBlock));
      given(kakaoLocalClient.getRegionCode("KakaoAK kakao-rest-api-key", longitude, latitude))
          .willReturn(kakaoResponse);
      given(kakaoRegionParser.toLocationNames(kakaoResponse))
          .willReturn(List.of("서울특별시", "중구", "명동", ""));

      // when
      List<String> result = locationResolver.resolveLocationNames(latitude, longitude);

      // then
      assertThat(result).containsExactly("서울특별시", "중구", "명동", "");
      verify(locationBlockRepository).insertIfAbsent(any(), eq(block.latBlock()),
          eq(block.lonBlock()), eq("[\"서울특별시\",\"중구\",\"명동\",\"\"]"));
    }
  }
}