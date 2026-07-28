package com.sprint.mission.otboo.domain.weathernotification.weather.mapper;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.HumidityDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WindSpeedDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeatherMapper {

  public WeatherDto toDto(Weather weather, WeatherGrid weatherGrid, double latitude,
      double longitude, List<String> locationNames) {
    LocationDto locationDto = new LocationDto(
        latitude,
        longitude,
        weatherGrid.getX(),
        weatherGrid.getY(),
        locationNames
    );

    PrecipitationDto precipitation = new PrecipitationDto(
        weather.getPrecipitationType(),
        weather.getPrecipitationAmount(),
        weather.getPrecipitationProbability()
    );

    HumidityDto humidity = new HumidityDto(
        weather.getHumidityCurrent(),
        weather.getHumidityCompared()
    );

    TemperatureDto temperature = new TemperatureDto(
        weather.getTemperatureCurrent(),
        weather.getTemperatureCompared(),
        weather.getTemperatureMin(),
        weather.getTemperatureMax()
    );

    WindSpeedDto windSpeed = new WindSpeedDto(
        weather.getWindSpeed(),
        weather.getWindAsWord()
    );

    return new WeatherDto(
        weather.getId(),
        weather.getForecastedAt(),
        weather.getForecastAt(),
        locationDto,
        weather.getSkyStatus(),
        precipitation,
        humidity,
        temperature,
        windSpeed
    );
  }
}