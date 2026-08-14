package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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

  // parseDailyForecast()의 슬롯 단위 대체 - 날짜별 대표시각 1개로 압축하지 않고, distinct
  // fcstTime마다 슬롯 DTO를 그대로 만든다. now 파라미터가 없다 - 어느 슬롯도 "지금과 가장
  // 가까운 슬롯"으로 선별되지 않고 응답에 있는 슬롯을 전부 반환하기 때문이다.
  public List<WeatherForecastSlotDto> parseSlotForecast(KmaWeatherResponse response) {
    Map<String, List<Item>> itemsByDate = response.response().body().items().item().stream()
        .collect(Collectors.groupingBy(Item::fcstDate, TreeMap::new, Collectors.toList()));

    List<WeatherForecastSlotDto> result = new ArrayList<>();
    for (Map.Entry<String, List<Item>> entry : itemsByDate.entrySet()) {
      List<Item> dayItems = entry.getValue();
      if (!hasEnoughSlots(dayItems) || !hasTemperatureData(dayItems)) {
        continue;
      }
      LocalDate date = LocalDate.parse(entry.getKey(), DATE_FORMATTER);
      DailyTemperatureRange range = temperatureRange(dayItems);
      for (String slotTime : dayItems.stream().map(Item::fcstTime).distinct().toList()) {
        toSlotForecast(date, slotTime, dayItems, range).ifPresent(result::add);
      }
    }
    return result;
  }

  private DailyTemperatureRange temperatureRange(List<Item> dayItems) {
    double tempMin = Double.MAX_VALUE;
    double tempMax = -Double.MAX_VALUE;
    for (Item item : dayItems) {
      if ("TMP".equals(item.category())) {
        double value = Double.parseDouble(item.fcstValue());
        tempMin = Math.min(tempMin, value);
        tempMax = Math.max(tempMax, value);
      }
    }
    return new DailyTemperatureRange(tempMin, tempMax);
  }

  private Optional<WeatherForecastSlotDto> toSlotForecast(LocalDate date, String slotTime,
      List<Item> dayItems, DailyTemperatureRange range) {
    Double tempCurrent = null;
    double humidityCurrent = 0.0;
    double windSpeed = 0.0;
    SkyStatus skyStatus = SkyStatus.CLEAR;
    PrecipitationType precipitationType = PrecipitationType.NONE;
    double precipitationAmount = 0.0;
    double precipitationProbability = 0.0;

    for (Item item : dayItems) {
      if (!slotTime.equals(item.fcstTime())) {
        continue;
      }
      switch (item.category()) {
        case "TMP" -> tempCurrent = Double.parseDouble(item.fcstValue());
        case "SKY" -> skyStatus = toSkyStatus(item.fcstValue());
        case "REH" -> humidityCurrent = Double.parseDouble(item.fcstValue());
        case "WSD" -> windSpeed = Double.parseDouble(item.fcstValue());
        case "POP" -> precipitationProbability = Double.parseDouble(item.fcstValue());
        case "PCP" -> precipitationAmount = parsePrecipitationAmount(item.fcstValue());
        case "PTY" -> {
          if (!"0".equals(item.fcstValue())) {
            precipitationType = toPrecipitationType(item.fcstValue());
          }
        }
        default -> {
        }
      }
    }

    if (tempCurrent == null) {
      return Optional.empty();
    }
    return Optional.of(new WeatherForecastSlotDto(date, toInstant(date, slotTime), skyStatus,
        precipitationType, precipitationAmount, precipitationProbability, humidityCurrent,
        tempCurrent, range.min(), range.max(), windSpeed));
  }

  private Instant toInstant(LocalDate date, String slotTime) {
    int hour = Integer.parseInt(slotTime.substring(0, 2));
    int minute = Integer.parseInt(slotTime.substring(2, 4));
    return date.atTime(hour, minute).atZone(KST).toInstant();
  }

  private record DailyTemperatureRange(double min, double max) {

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
          PrecipitationType candidate =
              priority >= 0 ? toPrecipitationType(item.fcstValue()) : PrecipitationType.NONE;
          if (priority > precipitationPriority) {
            precipitationPriority = priority;
            precipitationType = candidate;
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
      default -> {
        log.warn("알 수 없는 PTY 코드: {}", pty);
        yield PrecipitationType.NONE;
      }
    };
  }

  private SkyStatus toSkyStatus(String skyCode) {
    return switch (skyCode) {
      case "1" -> SkyStatus.CLEAR;
      case "3" -> SkyStatus.MOSTLY_CLOUDY;
      case "4" -> SkyStatus.CLOUDY;
      default -> {
        log.warn("알 수 없는 SKY 코드: {}", skyCode);
        yield SkyStatus.CLEAR;
      }
    };
  }
}