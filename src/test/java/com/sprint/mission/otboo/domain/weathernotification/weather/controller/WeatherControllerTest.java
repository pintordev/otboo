package com.sprint.mission.otboo.domain.weathernotification.weather.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.exception.InvalidCoordinateException;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private WeatherService weatherService;

  @Nested
  @DisplayName("GetWeather")
  class GetWeather {

    @Test
    @DisplayName("유효한_좌표로_조회하면_200과_WeatherDto_목록을_반환한다")
    void 유효한_좌표로_조회하면_200과_WeatherDto_목록을_반환한다() throws Exception {
      // given
      WeatherDto weatherDto = FIXTURE_MONKEY.giveMeBuilder(WeatherDto.class)
          .set("skyStatus", SkyStatus.CLEAR)
          .set("location.x", 60)
          .set("temperature.current", 28.0)
          .sample();
      given(weatherService.getWeather(anyDouble(), anyDouble())).willReturn(List.of(weatherDto));

      // when & then
      mockMvc.perform(get("/api/weathers")
              .param("longitude", "126.9884121")
              .param("latitude", "37.5674783"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].skyStatus").value("CLEAR"))
          .andExpect(jsonPath("$[0].location.x").value(60))
          .andExpect(jsonPath("$[0].temperature.current").value(28.0));
    }

    @Test
    @DisplayName("한반도_범위_밖_좌표로_조회하면_400을_반환한다")
    void 한반도_범위_밖_좌표로_조회하면_400을_반환한다() throws Exception {
      // given
      given(weatherService.getWeather(anyDouble(), anyDouble()))
          .willThrow(InvalidCoordinateException.of(10.0, 127.0));

      // when & then
      mockMvc.perform(get("/api/weathers")
              .param("longitude", "127.0")
              .param("latitude", "10.0"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("GetWeatherLocation")
  class GetWeatherLocation {

    @Test
    @DisplayName("유효한_좌표로_조회하면_200과_LocationDto를_반환한다")
    void 유효한_좌표로_조회하면_200과_LocationDto를_반환한다() throws Exception {
      // given
      LocationDto locationDto = FIXTURE_MONKEY.giveMeBuilder(LocationDto.class)
          .set("latitude", 37.5674783)
          .set("longitude", 126.9884121)
          .set("x", 60)
          .set("y", 127)
          .set("locationNames", List.of("서울특별시", "중구", "명동"))
          .sample();
      given(weatherService.getLocation(anyDouble(), anyDouble())).willReturn(locationDto);

      // when & then
      mockMvc.perform(get("/api/weathers/location")
              .param("longitude", "126.9884121")
              .param("latitude", "37.5674783"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.x").value(60))
          .andExpect(jsonPath("$.y").value(127))
          .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"));
      verify(weatherService).getLocation(37.5674783, 126.9884121);
    }

    @Test
    @DisplayName("한반도_범위_밖_좌표로_조회하면_400을_반환한다")
    void 한반도_범위_밖_좌표로_조회하면_400을_반환한다() throws Exception {
      // given
      given(weatherService.getLocation(anyDouble(), anyDouble()))
          .willThrow(InvalidCoordinateException.of(10.0, 127.0));

      // when & then
      mockMvc.perform(get("/api/weathers/location")
              .param("longitude", "127.0")
              .param("latitude", "10.0"))
          .andExpect(status().isBadRequest());
    }
  }
}