package com.sprint.mission.otboo.domain.weathernotification.weather.controller.api;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "날씨 관리", description = "날씨 관련 API")
public interface WeatherApi {

  @Operation(summary = "날씨 정보 조회", description = "날씨 정보 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "날씨 조회 성공"),
      @ApiResponse(responseCode = "400", description = "날씨 조회 실패")
  })
  ResponseEntity<List<WeatherDto>> getWeather(double longitude, double latitude);

  @Operation(summary = "날씨 위치 정보 조회", description = "날씨 위치 정보 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "위치 정보 조회 성공"),
      @ApiResponse(responseCode = "400", description = "위치 정보 조회 실패")
  })
  ResponseEntity<LocationDto> getWeatherLocation(double longitude, double latitude);
}