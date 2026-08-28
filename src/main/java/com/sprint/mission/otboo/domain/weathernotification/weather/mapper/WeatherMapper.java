package com.sprint.mission.otboo.domain.weathernotification.weather.mapper;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.HumidityDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WindSpeedDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeatherMapper {

  // isToday=true(오늘)는 그 슬롯의 실제값을, false(내일 이후)는 그날 일별 요약값(최악
  // 하늘상태/최다 강수형태/최대 습도)을 쓴다 - 일별 요약 컬럼이 아직 안 채워졌으면(마이그레이션
  // 직후 등) null이므로 그 경우도 실제값으로 폴백한다.
  public WeatherDto toDto(Weather weather, WeatherGrid weatherGrid, double latitude,
      double longitude, List<String> locationNames, boolean isToday) {
    LocationDto locationDto = new LocationDto(
        latitude,
        longitude,
        weatherGrid.getX(),
        weatherGrid.getY(),
        locationNames
    );

    SkyStatus skyStatus = isToday || weather.getSkyStatusWorst() == null
        ? weather.getSkyStatus() : weather.getSkyStatusWorst();
    PrecipitationType precipitationType = isToday || weather.getPrecipitationTypeMode() == null
        ? weather.getPrecipitationType() : weather.getPrecipitationTypeMode();
    double humidityCurrent = isToday || weather.getHumidityMax() == null
        ? weather.getHumidityCurrent() : weather.getHumidityMax();

    PrecipitationDto precipitation = new PrecipitationDto(
        precipitationType,
        weather.getPrecipitationAmount(),
        weather.getPrecipitationProbability()
    );

    HumidityDto humidity = new HumidityDto(
        humidityCurrent,
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
        skyStatus,
        precipitation,
        humidity,
        temperature,
        windSpeed
    );
  }
}