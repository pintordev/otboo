package com.sprint.mission.otboo.domain.clothesrecommend.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.clothesrecommend.chatbot.mapper.ChatbotWardrobeAssembler;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity.Clothes;
import com.sprint.mission.otboo.domain.clothesrecommend.clothes.repository.ClothesRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.llm.dto.LlmChatbotContext;
import com.sprint.mission.otboo.external.llm.dto.LlmChatbotWardrobeItem;
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
class ChatbotContextProviderTest {

  private static final String QUESTION = "오늘 뭐 입을까?";

  @InjectMocks
  ChatbotContextProvider chatbotContextProvider;

  @Mock
  WeatherRepository weatherRepository;
  @Mock
  ProfileRepository profileRepository;
  @Mock
  ClothesRepository clothesRepository;
  @Mock
  ChatbotWardrobeAssembler chatbotWardrobeAssembler;

  private static Weather createWeather(double temperature, PrecipitationType precipitationType,
      WindStrength windStrength) {
    Weather weather = Weather.create(
        null, null, null,
        SkyStatus.CLOUDY, precipitationType, 0, 0,
        0, 0.0,
        temperature, 0.0, temperature - 3, temperature + 3,
        windStrength == WindStrength.STRONG ? 15.0 : 3.0,
        windStrength,
        null, null, null, null, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0);
    ReflectionTestUtils.setField(weather, "id", UUID.randomUUID());
    return weather;
  }

  private static Profile createProfile(UUID userId, int temperatureSensitivity) {
    Profile profile = Profile.create(null);
    ReflectionTestUtils.setField(profile, "id", userId);
    ReflectionTestUtils.setField(profile, "temperatureSensitivity", temperatureSensitivity);
    return profile;
  }

  private static Clothes createClothes(UUID ownerId, String name, ClothesType type) {
    Clothes clothes = Clothes.create(ownerId, name, type);
    ReflectionTestUtils.setField(clothes, "id", UUID.randomUUID());
    return clothes;
  }

  private void givenEmptyWardrobe(UUID userId) {
    given(clothesRepository.findActiveByOwnerId(userId)).willReturn(List.of());
    given(chatbotWardrobeAssembler.toWardrobeItems(List.of())).willReturn(List.of());
  }

  @Nested
  @DisplayName("컨텍스트 수집")
  class Collect {

    @Test
    @DisplayName("날씨_프로필_옷장을_모아_컨텍스트를_만든다")
    void 날씨_프로필_옷장을_모아_컨텍스트를_만든다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID weatherId = UUID.randomUUID();
      Weather weather = createWeather(28.0, PrecipitationType.NONE, WindStrength.WEAK);
      Clothes top = createClothes(userId, "리넨 셔츠", ClothesType.TOP);

      given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
      given(profileRepository.findById(userId)).willReturn(Optional.of(createProfile(userId, 4)));
      given(clothesRepository.findActiveByOwnerId(userId)).willReturn(List.of(top));
      given(chatbotWardrobeAssembler.toWardrobeItems(List.of(top)))
          .willReturn(List.of(new LlmChatbotWardrobeItem("리넨 셔츠", ClothesType.TOP, "")));

      // when
      LlmChatbotContext context = chatbotContextProvider.collect(userId, QUESTION, weatherId);

      // then
      assertThat(context.question()).isEqualTo(QUESTION);
      assertThat(context.sensitivity()).isEqualTo(4);
      assertThat(context.weather().temperature()).isEqualTo(28.0);
      assertThat(context.weather().precipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(context.weather().windStrength()).isEqualTo(WindStrength.WEAK);
      assertThat(context.wardrobe()).hasSize(1);
    }

    @Test
    @DisplayName("weatherId가_없으면_날씨_없이_수집한다")
    void weatherId가_없으면_날씨_없이_수집한다() {
      // given
      UUID userId = UUID.randomUUID();
      given(profileRepository.findById(userId)).willReturn(Optional.of(createProfile(userId, 3)));
      givenEmptyWardrobe(userId);

      // when
      LlmChatbotContext context = chatbotContextProvider.collect(userId, QUESTION, null);

      // then
      assertThat(context.weather()).isNull();
      verifyNoInteractions(weatherRepository);
    }

    @Test
    @DisplayName("weatherId에_해당하는_날씨가_없으면_날씨_없이_수집한다")
    void weatherId에_해당하는_날씨가_없으면_날씨_없이_수집한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID weatherId = UUID.randomUUID();

      given(weatherRepository.findById(weatherId)).willReturn(Optional.empty());
      given(profileRepository.findById(userId)).willReturn(Optional.of(createProfile(userId, 3)));
      givenEmptyWardrobe(userId);

      // when
      LlmChatbotContext context = chatbotContextProvider.collect(userId, QUESTION, weatherId);

      // then
      assertThat(context.weather()).isNull();
    }

    @Test
    @DisplayName("프로필이_없으면_기본_온도민감도를_쓴다")
    void 프로필이_없으면_기본_온도민감도를_쓴다() {
      // given
      UUID userId = UUID.randomUUID();
      given(profileRepository.findById(userId)).willReturn(Optional.empty());
      givenEmptyWardrobe(userId);

      // when
      LlmChatbotContext context = chatbotContextProvider.collect(userId, QUESTION, null);

      // then
      assertThat(context.sensitivity()).isEqualTo(3);
    }
  }
}
