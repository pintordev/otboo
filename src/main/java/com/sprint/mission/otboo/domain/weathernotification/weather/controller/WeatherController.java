package com.sprint.mission.otboo.domain.weathernotification.weather.controller;

import com.sprint.mission.otboo.domain.weathernotification.weather.controller.api.WeatherApi;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherService;
import com.sprint.mission.otboo.global.dto.ErrorResponse;
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

  // WeatherService 내부 조회 자체가 5초 orTimeout을 쓰므로, DeferredResult 타이머가 그보다
  // 먼저 끝나면 DB 폴백이 끝나기 전에 요청이 만료된다. grid/캐시 조회·폴백 시간까지 감안해 여유를 둔다.
  private static final long TIMEOUT_MILLIS = 8_000L;

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
                .body(toErrorResponse(ex)));
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
                .body(toErrorResponse(ex)));
          } else {
            deferredResult.setResult(ResponseEntity.ok(result));
          }
        });
    return deferredResult;
  }

  private ErrorResponse toErrorResponse(Throwable ex) {
    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
    return new ErrorResponse(cause.getClass().getSimpleName(), "날씨 조회 서비스를 일시적으로 이용할 수 없습니다.",
        null);
  }
}