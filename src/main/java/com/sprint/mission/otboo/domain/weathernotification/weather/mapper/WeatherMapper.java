package com.sprint.mission.otboo.domain.weathernotification.weather.mapper;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.HumidityDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WindSpeedDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import org.springframework.stereotype.Component;

@Component
public class WeatherMapper {

  public WeatherDto toDto(Weather weather) {
    Location location = weather.getLocation();

    LocationDto locationDto = new LocationDto(
        location.getLatitude(),
        location.getLongitude(),
        location.getX(),
        location.getY(),
        location.getLocationNames()
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