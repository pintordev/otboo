package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class KmaForecastParser {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String FUTURE_REPRESENTATIVE_TIME = "1500";

  public List<DailyWeatherForecastDto> parseDailyForecast(KmaWeatherResponse response,
      Instant now) {
    Map<String, List<Item>> itemsByDate = response.response().body().items().item().stream()
        .collect(Collectors.groupingBy(Item::fcstDate, TreeMap::new, Collectors.toList()));

    List<DailyWeatherForecastDto> result = new ArrayList<>();
    for (Map.Entry<String, List<Item>> entry : itemsByDate.entrySet()) {
      LocalDate date = LocalDate.parse(entry.getKey(), DATE_FORMATTER);
      result.add(toDailyForecast(date, entry.getValue(), FUTURE_REPRESENTATIVE_TIME));
    }
    return result;
  }

  private DailyWeatherForecastDto toDailyForecast(LocalDate date, List<Item> dayItems,
      String representativeTime) {
    double tempMin = Double.MAX_VALUE;
    double tempMax = -Double.MAX_VALUE;
    double tempCurrent = 0.0;
    double humidityCurrent = 0.0;
    double windSpeed = 0.0;
    SkyStatus skyStatus = SkyStatus.CLEAR;
    PrecipitationType precipitationType = PrecipitationType.NONE;
    double precipitationAmount = 0.0;
    double precipitationProbability = 0.0;

    for (Item item : dayItems) {
      switch (item.category()) {
        case "TMP" -> {
          double value = Double.parseDouble(item.fcstValue());
          tempMin = Math.min(tempMin, value);
          tempMax = Math.max(tempMax, value);
          if (representativeTime.equals(item.fcstTime())) {
            tempCurrent = value;
          }
        }
        case "SKY" -> {
          if (representativeTime.equals(item.fcstTime())) {
            skyStatus = toSkyStatus(item.fcstValue());
          }
        }
        case "REH" -> {
          if (representativeTime.equals(item.fcstTime())) {
            humidityCurrent = Double.parseDouble(item.fcstValue());
          }
        }
        case "WSD" -> {
          if (representativeTime.equals(item.fcstTime())) {
            windSpeed = Double.parseDouble(item.fcstValue());
          }
        }
        case "POP" -> {
          double value = Double.parseDouble(item.fcstValue());
          precipitationProbability = Math.max(precipitationProbability, value);
        }
        default -> {
        }
      }
    }

    return new DailyWeatherForecastDto(date, skyStatus, precipitationType, precipitationAmount,
        precipitationProbability, humidityCurrent, tempCurrent, tempMin, tempMax, windSpeed);
  }

  private SkyStatus toSkyStatus(String skyCode) {
    return switch (skyCode) {
      case "1" -> SkyStatus.CLEAR;
      case "3" -> SkyStatus.MOSTLY_CLOUDY;
      case "4" -> SkyStatus.CLOUDY;
      default -> SkyStatus.CLEAR;
    };
  }
}