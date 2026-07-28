package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KmaForecastParser {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String FUTURE_REPRESENTATIVE_TIME = "1500";
  private static final int MIN_SLOT_COUNT = 4;

  public List<DailyWeatherForecastDto> parseDailyForecast(KmaWeatherResponse response,
      Instant now) {
    Map<String, List<Item>> itemsByDate = response.response().body().items().item().stream()
        .collect(Collectors.groupingBy(Item::fcstDate, TreeMap::new, Collectors.toList()));

    LocalDate today = now.atZone(KST).toLocalDate();

    List<DailyWeatherForecastDto> result = new ArrayList<>();
    for (Map.Entry<String, List<Item>> entry : itemsByDate.entrySet()) {
      List<Item> dayItems = entry.getValue();
      if (!hasEnoughSlots(dayItems) || !hasTemperatureData(dayItems)) {
        continue;
      }

      LocalDate date = LocalDate.parse(entry.getKey(), DATE_FORMATTER);
      String representativeTime = date.isEqual(today)
          ? closestFcstTime(dayItems, now)
          : FUTURE_REPRESENTATIVE_TIME;
      result.add(toDailyForecast(date, dayItems, representativeTime));
    }
    return result;
  }

  private boolean hasEnoughSlots(List<Item> dayItems) {
    long distinctSlotCount = dayItems.stream().map(Item::fcstTime).distinct().count();
    return distinctSlotCount >= MIN_SLOT_COUNT;
  }

  private boolean hasTemperatureData(List<Item> dayItems) {
    return dayItems.stream().anyMatch(item -> "TMP".equals(item.category()));
  }

  private String closestFcstTime(List<Item> dayItems, Instant now) {
    ZonedDateTime nowKst = now.atZone(KST);
    int nowMinutes = nowKst.getHour() * 60 + nowKst.getMinute();

    return dayItems.stream()
        .map(Item::fcstTime)
        .distinct()
        .min(Comparator.comparingInt(fcstTime -> Math.abs(toMinutes(fcstTime) - nowMinutes)))
        .orElseThrow();
  }

  private int toMinutes(String fcstTime) {
    int hour = Integer.parseInt(fcstTime.substring(0, 2));
    int minute = Integer.parseInt(fcstTime.substring(2, 4));
    return hour * 60 + minute;
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
    int precipitationPriority = -1;

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
        case "PCP" -> precipitationAmount += parsePrecipitationAmount(item.fcstValue());
        case "PTY" -> {
          int priority = parsePtyPriority(item.fcstValue(), item.fcstTime(), representativeTime);
          if (priority > precipitationPriority) {
            precipitationPriority = priority;
            precipitationType = toPrecipitationType(item.fcstValue());
          }
        }
        default -> {
        }
      }
    }

    return new DailyWeatherForecastDto(date, skyStatus, precipitationType, precipitationAmount,
        precipitationProbability, humidityCurrent, tempCurrent, tempMin, tempMax, windSpeed);
  }

  private double parsePrecipitationAmount(String fcstValue) {
    if (fcstValue == null || fcstValue.isBlank() || fcstValue.contains("강수없음")) {
      return 0.0;
    }
    String cleaned = fcstValue.replace("mm", "").replace(" ", "");
    if (cleaned.contains("미만")) {
      return Math.max(0.0, Double.parseDouble(cleaned.replace("미만", "")) - 0.1);
    }
    if (cleaned.contains("~")) {
      return Double.parseDouble(cleaned.split("~")[0]);
    }
    return Double.parseDouble(cleaned);
  }

  private int parsePtyPriority(String pty, String fcstTime, String representativeTime) {
    if (pty == null || "0".equals(pty)) {
      return -1;
    }
    int timePriority = representativeTime.equals(fcstTime) ? 100 : 0;
    return timePriority + Integer.parseInt(pty);
  }

  private PrecipitationType toPrecipitationType(String pty) {
    return switch (pty) {
      case "1" -> PrecipitationType.RAIN;
      case "2" -> PrecipitationType.RAIN_SNOW;
      case "3" -> PrecipitationType.SNOW;
      case "4" -> PrecipitationType.SHOWER;
      default -> PrecipitationType.NONE;
    };
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