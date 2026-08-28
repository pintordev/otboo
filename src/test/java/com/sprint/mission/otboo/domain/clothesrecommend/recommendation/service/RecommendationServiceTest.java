package com.sprint.mission.otboo.domain.clothesrecommend.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

  @Mock
  LlmRecommendationRefiner llmRecommendationRefiner;

  // 조합 구성은 실제 동작이 필요하다 — 무작위 선택과 종류 배타 규칙을 그대로 검증한다.
  @Spy
  OutfitComposer outfitComposer = new OutfitComposer();

  @BeforeEach
  void setUpLlmRefinerDefault() {
    // 기본 동작: 후보 전체를 그대로 후보군으로 통과시켜 규칙 기반 동작만 검증되게 함
    lenient().when(llmRecommendationRefiner.selectPool(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
  }

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
    , null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
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
    @DisplayName("따뜻한_날씨에서_드레스가_선택되면_상의_하의는_함께_추천되지_않는다")
    void 따뜻한_날씨에서_드레스가_선택되면_상의_하의는_함께_추천되지_않는다() {
      // given — 22°C → DRESS와 TOP/BOTTOM은 배타. 둘 중 무엇을 입을지는 매번 달라진다.
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

      // when & then — 어느 쪽이 뽑히든 원피스와 상·하의가 섞이지는 않는다
      Set<Boolean> dressPicked = new HashSet<>();
      for (int i = 0; i < 30; i++) {
        List<OotdDto> clothes = recommendationService.recommend(weatherId, userId).clothes();
        boolean hasDress = clothes.stream().anyMatch(o -> o.type() == ClothesType.DRESS);
        boolean hasSeparates = clothes.stream()
            .anyMatch(o -> o.type() == ClothesType.TOP || o.type() == ClothesType.BOTTOM);

        assertThat(hasDress && hasSeparates)
            .as("원피스와 상·하의가 동시에 추천됨: %s", clothes)
            .isFalse();
        assertThat(hasDress || hasSeparates).isTrue();
        dressPicked.add(hasDress);
      }

      assertThat(dressPicked)
          .as("30회 중 원피스와 상·하의가 모두 한 번씩은 나와야 한다")
          .containsExactlyInAnyOrder(true, false);
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
    @DisplayName("같은_타입_옷이_여러벌이면_그중_한_벌이_선택된다")
    void 같은_타입_옷이_여러벌이면_그중_한_벌이_선택된다() {
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
          .satisfies(ootd -> assertThat(ootd.name()).isIn("오래된 반팔", "새 반팔"));
    }

    @Test
    @DisplayName("LLM_리파이너가_추린_후보군_안에서만_조합이_만들어진다")
    void LLM_리파이너가_추린_후보군_안에서만_조합이_만들어진다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes excludedTop = createClothes(userId, "후보군에서 빠진 반팔", ClothesType.TOP);
      Clothes llmTop = createClothes(userId, "LLM 선택 반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(excludedTop, llmTop, bottom, shoes));
      given(llmRecommendationRefiner.selectPool(any(), any()))
          .willReturn(List.of(llmTop, bottom, shoes));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes())
          .anyMatch(ootd -> ootd.name().equals("LLM 선택 반팔"));
      assertThat(result.clothes())
          .noneMatch(ootd -> ootd.name().equals("후보군에서 빠진 반팔"));
    }
  }

  @Nested
  @DisplayName("후보군 종류 보정")
  class TypeCoverage {

    @Test
    @DisplayName("LLM이 특정 종류를 통째로 빠뜨려도 보유한 옷으로 채워 넣는다")
    void LLM이_특정_종류를_통째로_빠뜨려도_보유한_옷으로_채워_넣는다() {
      // given - LLM 후보군에 신발이 없지만 사용자는 신발을 가지고 있다
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);
      Clothes shoes = createClothes(userId, "운동화", ClothesType.SHOES);
      Clothes bag = createClothes(userId, "백팩", ClothesType.BAG);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom, shoes, bag));
      given(llmRecommendationRefiner.selectPool(any(), any()))
          .willReturn(List.of(top, bottom));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes()).extracting(OotdDto::type)
          .contains(ClothesType.SHOES, ClothesType.BAG);
    }

    @Test
    @DisplayName("보유하지 않은 종류는 채워 넣지 않는다")
    void 보유하지_않은_종류는_채워_넣지_않는다() {
      // given - 신발을 아예 가지고 있지 않다
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes top = createClothes(userId, "반팔", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(top, bottom));
      given(llmRecommendationRefiner.selectPool(any(), any()))
          .willReturn(List.of(top, bottom));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when
      RecommendationDto result = recommendationService.recommend(weatherId, userId);

      // then
      assertThat(result.clothes()).extracting(OotdDto::type)
          .doesNotContain(ClothesType.SHOES);
    }

    @Test
    @DisplayName("LLM이 고른 종류는 LLM 선택을 그대로 존중한다")
    void LLM이_고른_종류는_LLM_선택을_그대로_존중한다() {
      // given - 상의는 LLM이 한 벌만 골랐다. 보정이 나머지 상의를 되살리면 안 된다.
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Clothes pickedTop = createClothes(userId, "LLM이 고른 반팔", ClothesType.TOP);
      Clothes ignoredTop = createClothes(userId, "LLM이 뺀 니트", ClothesType.TOP);
      Clothes bottom = createClothes(userId, "반바지", ClothesType.BOTTOM);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(List.of(pickedTop, ignoredTop, bottom));
      given(llmRecommendationRefiner.selectPool(any(), any()))
          .willReturn(List.of(pickedTop, bottom));
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when & then
      for (int i = 0; i < 20; i++) {
        assertThat(recommendationService.recommend(weatherId, userId).clothes())
            .noneMatch(ootd -> ootd.name().equals("LLM이 뺀 니트"));
      }
    }
  }

  @Nested
  @DisplayName("추천 결과 다양성")
  class Variety {

    @Test
    @DisplayName("같은 요청을 반복해도 후보가 여러 벌이면 서로 다른 조합이 나온다")
    void 같은_요청을_반복해도_후보가_여러_벌이면_서로_다른_조합이_나온다() {
      // given - 종류마다 후보를 3벌씩 두어 조합이 달라질 여지를 만든다
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Instant base = Instant.parse("2026-08-01T00:00:00Z");
      List<Clothes> wardrobe = List.of(
          createClothesWithCreatedAt(userId, "반팔 티셔츠", ClothesType.TOP, base),
          createClothesWithCreatedAt(userId, "긴팔 티셔츠", ClothesType.TOP, base.plusSeconds(1)),
          createClothesWithCreatedAt(userId, "맨투맨", ClothesType.TOP, base.plusSeconds(2)),
          createClothesWithCreatedAt(userId, "청바지", ClothesType.BOTTOM, base.plusSeconds(3)),
          createClothesWithCreatedAt(userId, "슬랙스", ClothesType.BOTTOM, base.plusSeconds(4)),
          createClothesWithCreatedAt(userId, "반바지", ClothesType.BOTTOM, base.plusSeconds(5)),
          createClothesWithCreatedAt(userId, "운동화", ClothesType.SHOES, base.plusSeconds(6)),
          createClothesWithCreatedAt(userId, "구두", ClothesType.SHOES, base.plusSeconds(7)),
          createClothesWithCreatedAt(userId, "샌들", ClothesType.SHOES, base.plusSeconds(8)));

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(wardrobe);
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when - 같은 입력으로 여러 번 호출한다
      Set<List<String>> distinctResults = new HashSet<>();
      for (int i = 0; i < 30; i++) {
        distinctResults.add(recommendationService.recommend(weatherId, userId).clothes()
            .stream().map(OotdDto::name).toList());
      }

      // then
      assertThat(distinctResults)
          .as("30회 호출에서 나온 서로 다른 조합의 수")
          .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("조합에는 종류마다 한 벌만 담긴다")
    void 조합에는_종류마다_한_벌만_담긴다() {
      // given
      UUID weatherId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Weather weather = createWeather(25.0, PrecipitationType.NONE,
          SkyStatus.CLEAR, WindStrength.WEAK);
      Profile profile = createProfile(userId, 3);

      Instant base = Instant.parse("2026-08-01T00:00:00Z");
      List<Clothes> wardrobe = List.of(
          createClothesWithCreatedAt(userId, "반팔 티셔츠", ClothesType.TOP, base),
          createClothesWithCreatedAt(userId, "긴팔 티셔츠", ClothesType.TOP, base.plusSeconds(1)),
          createClothesWithCreatedAt(userId, "청바지", ClothesType.BOTTOM, base.plusSeconds(2)),
          createClothesWithCreatedAt(userId, "슬랙스", ClothesType.BOTTOM, base.plusSeconds(3)),
          createClothesWithCreatedAt(userId, "운동화", ClothesType.SHOES, base.plusSeconds(4)));

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findByIdWithUser(userId)).willReturn(Optional.of(profile));
      given(clothesRepository.findActiveByOwnerIdAndTypeIn(eq(userId), anyCollection()))
          .willReturn(wardrobe);
      given(recommendationMapper.toOotdDtoList(any()))
          .willAnswer(inv -> toOotdStub(inv.getArgument(0)));

      // when & then
      for (int i = 0; i < 20; i++) {
        List<OotdDto> clothes = recommendationService.recommend(weatherId, userId).clothes();
        assertThat(clothes.stream().map(OotdDto::type).toList())
            .doesNotHaveDuplicates();
      }
    }
  }

  @Nested
  @DisplayName("체감 온도별 추천 의상 종류")
  class GetRecommendedTypes {

    @Test
    @DisplayName("혹한이면 아우터와 목도리, 양말까지 추천한다")
    void 혹한이면_아우터와_목도리_양말까지_추천한다() {
      // when
      Set<ClothesType> types = recommendationService.getRecommendedTypes(0.0);

      // then
      assertThat(types).containsExactlyInAnyOrder(
          ClothesType.SHOES, ClothesType.BAG, ClothesType.ACCESSORY,
          ClothesType.TOP, ClothesType.BOTTOM,
          ClothesType.OUTER, ClothesType.SCARF, ClothesType.SOCKS);
    }

    @Test
    @DisplayName("추위면 목도리 없이 양말까지 추천한다")
    void 추위면_목도리_없이_양말까지_추천한다() {
      // when
      Set<ClothesType> types = recommendationService.getRecommendedTypes(6.0);

      // then
      assertThat(types).containsExactlyInAnyOrder(
          ClothesType.SHOES, ClothesType.BAG, ClothesType.ACCESSORY,
          ClothesType.TOP, ClothesType.BOTTOM,
          ClothesType.OUTER, ClothesType.SOCKS);
    }

    @Test
    @DisplayName("선선하면 아우터까지만 추천한다")
    void 선선하면_아우터까지만_추천한다() {
      // when
      Set<ClothesType> types = recommendationService.getRecommendedTypes(12.0);

      // then
      assertThat(types).containsExactlyInAnyOrder(
          ClothesType.SHOES, ClothesType.BAG, ClothesType.ACCESSORY,
          ClothesType.TOP, ClothesType.BOTTOM, ClothesType.OUTER);
    }

    @Test
    @DisplayName("따뜻하면 아우터를 추천하지 않는다")
    void 따뜻하면_아우터를_추천하지_않는다() {
      // when
      Set<ClothesType> types = recommendationService.getRecommendedTypes(24.0);

      // then
      assertThat(types).contains(ClothesType.SHOES);
      assertThat(types).doesNotContain(
          ClothesType.OUTER, ClothesType.SCARF, ClothesType.SOCKS);
    }

    @Test
    @DisplayName("날씨와 무관한 가방과 액세서리는 어떤 온도에서도 추천한다")
    void 날씨와_무관한_가방과_액세서리는_어떤_온도에서도_추천한다() {
      // when & then
      for (double temp : new double[] {-5.0, 0.0, 6.0, 12.0, 24.0, 30.0}) {
        assertThat(recommendationService.getRecommendedTypes(temp))
            .as("체감 %.1f도", temp)
            .contains(ClothesType.BAG, ClothesType.ACCESSORY);
      }
    }

    @Test
    @DisplayName("속옷과 기타는 어떤 온도에서도 추천하지 않는다")
    void 속옷과_기타는_어떤_온도에서도_추천하지_않는다() {
      // when & then
      for (double temp : new double[] {-5.0, 0.0, 6.0, 12.0, 24.0, 30.0}) {
        assertThat(recommendationService.getRecommendedTypes(temp))
            .as("체감 %.1f도", temp)
            .doesNotContain(ClothesType.UNDERWEAR, ClothesType.ETC);
      }
    }
  }
}
