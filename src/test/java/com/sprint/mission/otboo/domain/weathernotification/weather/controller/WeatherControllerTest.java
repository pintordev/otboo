package com.sprint.mission.otboo.domain.weathernotification.weather.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.HumidityDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherAPILocation;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WindSpeedDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

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
      WeatherDto weatherDto = new WeatherDto(
          UUID.randomUUID(), Instant.parse("2026-07-27T08:00:00Z"),
          Instant.parse("2026-07-27T00:00:00Z"),
          new WeatherAPILocation(37.5674783, 126.9884121, 60, 127, List.of("서울특별시")),
          SkyStatus.CLEAR,
          new PrecipitationDto(PrecipitationType.NONE, 0.0, 10.0),
          new HumidityDto(65.0, 2.0),
          new TemperatureDto(28.0, -1.0, 25.0, 31.0),
          new WindSpeedDto(2.5, WindStrength.WEAK)
      );
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
  }
}