package com.sprint.mission.otboo.domain.weathernotification.weather.controller;

import com.sprint.mission.otboo.domain.weathernotification.weather.controller.api.WeatherApi;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RequestMapping("/api/weathers")
@RequiredArgsConstructor
@RestController
public class WeatherController implements WeatherApi {

  private static final long TIMEOUT_MILLIS = 5_000L;

  private final WeatherService weatherService;

  @Override
  @GetMapping
  public DeferredResult<ResponseEntity<List<WeatherDto>>> getWeather(
      @RequestParam("longitude") double longitude,
      @RequestParam("latitude") double latitude) {
    DeferredResult<ResponseEntity<List<WeatherDto>>> deferredResult =
        new DeferredResult<>(TIMEOUT_MILLIS);
    weatherService.getWeatherAsync(latitude, longitude)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .build());
          } else {
            deferredResult.setResult(ResponseEntity.ok(result));
          }
        });
    return deferredResult;
  }

  @Override
  @GetMapping("/location")
  public DeferredResult<ResponseEntity<LocationDto>> getWeatherLocation(
      @RequestParam("longitude") double longitude,
      @RequestParam("latitude") double latitude) {
    DeferredResult<ResponseEntity<LocationDto>> deferredResult =
        new DeferredResult<>(TIMEOUT_MILLIS);
    weatherService.getLocationAsync(latitude, longitude)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .build());
          } else {
            deferredResult.setResult(ResponseEntity.ok(result));
          }
        });
    return deferredResult;
  }
}