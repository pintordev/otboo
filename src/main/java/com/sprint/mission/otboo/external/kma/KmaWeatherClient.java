package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kmaWeatherClient", url = "${weather.kma.base-url}")
public interface KmaWeatherClient {

  @GetMapping
  KmaWeatherResponse getVillageForecast(
      @RequestParam("serviceKey") String serviceKey,
      @RequestParam("numOfRows") int numOfRows,
      @RequestParam("pageNo") int pageNo,
      @RequestParam("dataType") String dataType,
      @RequestParam("base_date") String baseDate,
      @RequestParam("base_time") String baseTime,
      @RequestParam("nx") int nx,
      @RequestParam("ny") int ny
  );
}