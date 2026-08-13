package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.dto.RecommendationDto;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.exception.ProfileNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.exception.WeatherNotFoundException;
import com.sprint.mission.otboo.domain.clothesrecommend.recommendation.mapper.RecommendationOotdAssembler;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @InjectMocks
  RecommendationService recommendationService;

  @Mock
  WeatherRepository weatherRepository;

  @Mock
  ProfileRepository profileRepository;

  @Mock
  ClothesRepository clothesRepository;

  @Mock
  RecommendationOotdAssembler recommendationMapper;

  // --- 헬퍼 메서드 ---

  private Weather createWeather(double temperature, PrecipitationType precipitationType,
      SkyStatus skyStatus, WindStrength windStrength) {
    Weather w = Weather.create(
        null, null, null,
        skyStatus, precipitationType, 0, 0,
        0, 0.0,
        temperature, 0.0, temperature - 3, temperature + 3,
        windStrength == WindStrength.STRONG ? 15.0 : 3.0,
        windStrength
    );
    ReflectionTestUtils.setField(w, "id", UUID.randomUUID());
    return w;
  }

  private Profile createProfile(UUID userId, int temperatureSensitivity) {
    Profile profile = Profile.create(null);
    ReflectionTestUtils.setField(profile, "id", userId);
    ReflectionTestUtils.setField(profile, "temperatureSensitivity", temperatureSensitivity);
    return profile;
  }

  private Clothes createClothes(UUID ownerId, String name, ClothesType type) {
    Clothes clothes = Clothes.create(ownerId, name, type);
    ReflectionTestUtils.setField(clothes, "id", UUID.randomUUID());
    return clothes;
  }

  private Clothes createClothesWithCreatedAt(UUID ownerId, String name, ClothesType type,
      Instant createdAt) {
    Clothes clothes = Clothes.create(ownerId, name, type);
    ReflectionTestUtils.setField(clothes, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(clothes, "createdAt", createdAt);
    return clothes;
  }

  private List<OotdDto> toOotdStub(List<Clothes> selected) {
    return selected.stream()
        .map(c -> new OotdDto(c.getId(), c.getName(), null, c.getType(), List.of()))
        .toList();
  }

  // --- 테스트 ---

  @Nested
  @DisplayName("추천 조회")
  class Recommend {

    @Test
    @DisplayName("날씨_프로필_의상이_모두_있으면_추천_결과를_반환한다")
    void 날씨_프로필_의상이_모두_있으면_추천_결과를_반환한다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔 티셔츠", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.weatherId()).isEqualTo(weatherId);
      assertThat(result.userId()).isEqualTo(userId);
      assertThat(result.clothes()).isNotEmpty();
    }

    @Test
    @DisplayName("날씨_정보가_없으면_WeatherNotFoundException을_던진다")
    void 날씨_정보가_없으면_WeatherNotFoundException을_던진다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      given(weatherRepository.findById(weatherId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> recommendationService.recommend(weatherId, userId))
          .isInstanceOf(WeatherNotFoundException.class);
    }

    @Test
    @DisplayName("프로필_정보가_없으면_ProfileNotFoundException을_던진다")
    void 프로필_정보가_없으면_ProfileNotFoundException을_던진다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> recommendationService.recommend(weatherId, userId))
          .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    @DisplayName("보유_의상이_없으면_빈_리스트를_반환한다")
    void 보유_의상이_없으면_빈_리스트를_반환한다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of());

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes()).isEmpty();
    }

    @Test
    @DisplayName("비가_오면_아우터가_추천에_포함된다")
    void 비가_오면_아우터가_추천에_포함된다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.RAIN,
          SkyStatus.CLOUDY, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);
      Clothes outer = createClothes(userId, "레인코트", ClothesType.OUTER);
      Clothes hat = createClothes(userId, "모자", ClothesType.HAT);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, outer, hat));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.OUTER);
    }

    @Test
    @DisplayName("더운_날씨에서_모자가_추천에_포함된다")
    void 더운_날씨에서_모자가_추천에_포함된다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(30.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "민소매", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "샌들", ClothesType.SHOES);
      Clothes hat = createClothes(userId, "모자", ClothesType.HAT);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, hat));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.HAT);
    }

    @Test
    @DisplayName("눈이_오면_스카프와_양말이_추천에_포함된다")
    void 눈이_오면_스카프와_양말이_추천에_포함된다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(20.0, PrecipitationType.SNOW,
          SkyStatus.CLOUDY, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "맨투맨", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "청바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);
      Clothes outer = createClothes(userId, "패딩", ClothesType.OUTER);
      Clothes scarf = createClothes(userId, "목도리", ClothesType.SCARF);
      Clothes socks = createClothes(userId, "양말", ClothesType.SOCKS);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, outer, scarf, socks));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.SCARF);
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.SOCKS);
    }

    @Test
    @DisplayName("강풍이면_아우터가_추천에_포함된다")
    void 강풍이면_아우터가_추천에_포함된다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.STRONG);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);
      Clothes outer = createClothes(userId, "바람막이", ClothesType.OUTER);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, outer));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.OUTER);
    }

    @Test
    @DisplayName("필요한_타입의_옷이_없으면_해당_타입은_건너뛴다")
    void 필요한_타입의_옷이_없으면_해당_타입은_건너뛴다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(7.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "니트", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "청바지", ClothesType.BOTTOM);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes()).hasSize(2);
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.type() == ClothesType.OUTER);
    }

    @Test
    @DisplayName("따뜻한_날씨에서_드레스가_있으면_상의_하의_대신_드레스가_추천된다")
    void 따뜻한_날씨에서_드레스가_있으면_상의_하의_대신_드레스가_추천된다() {
      // given — 22°C → DRESS가 TOP/BOTTOM 대체
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(22.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);
      Clothes dress = createClothes(userId, "원피스", ClothesType.DRESS);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, dress));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.DRESS);
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.type() == ClothesType.TOP);
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.type() == ClothesType.BOTTOM);
    }

    @Test
    @DisplayName("따뜻한_날씨에서_드레스가_없으면_상의_하의가_추천된다")
    void 따뜻한_날씨에서_드레스가_없으면_상의_하의가_추천된다() {
      // given — 22°C, DRESS 없음 → TOP+BOTTOM 선택
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(22.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.TOP);
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.type() == ClothesType.BOTTOM);
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.type() == ClothesType.DRESS);
    }

    @Test
    @DisplayName("추운_날씨에서는_드레스가_추천에_포함되지_않는다")
    void 추운_날씨에서는_드레스가_추천에_포함되지_않는다() {
      // given — 7°C → 추움 → DRESS 미포함
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(7.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "니트", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "청바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "부츠", ClothesType.SHOES);
      Clothes dress = createClothes(userId, "원피스", ClothesType.DRESS);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, dress));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.type() == ClothesType.DRESS);
    }

    @Test
    @DisplayName("같은_타입_옷이_여러벌이면_가장_최근에_등록한_옷이_선택된다")
    void 같은_타입_옷이_여러벌이면_가장_최근에_등록한_옷이_선택된다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes oldTop = createClothesWithCreatedAt(userId, "오래된 반팔",
          ClothesType.TOP, Instant.parse("2025-01-01T00:00:00Z"));
      Clothes newTop = createClothesWithCreatedAt(userId, "새 반팔",
          ClothesType.TOP, Instant.parse("2025-07-01T00:00:00Z"));
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(oldTop, newTop, bottom, shoes));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .filteredOn(ootd -> ootd.type() == ClothesType.TOP)
          .hasSize(1)
          .first()
          .satisfies(ootd -> assertThat(ootd.name()).isEqualTo("새 반팔"));
    }
  }
}
