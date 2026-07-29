package com.sprint.mission.otboo.domain.weathernotification.weather.controller;

import com.sprint.mission.otboo.domain.weathernotification.weather.controller.api.WeatherApi;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/weathers")
@RequiredArgsConstructor
@RestController
public class WeatherController implements WeatherApi {

  private final WeatherService weatherService;

  @Override
  @GetMapping
  public ResponseEntity<List<WeatherDto>> getWeather(
      @RequestParam("longitude") double longitude,
      @RequestParam("latitude") double latitude) {
    return ResponseEntity.ok(weatherService.getWeather(latitude, longitude));
  }

  @Override
  @GetMapping("/location")
  public ResponseEntity<LocationDto> getWeatherLocation(
      @RequestParam("longitude") double longitude,
      @RequestParam("latitude") double latitude) {
    return ResponseEntity.ok(weatherService.getLocation(latitude, longitude));
  }
}