package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse.Item;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
  // 예보 범위 끝자락 날짜는 항상 3시간 간격 그리드(하루 8개)로 온다 - 실응답으로 확인됨.
  private static final int FULL_DAY_SLOT_COUNT = 8;

  // 슬롯 단위 파싱 - 날짜별 대표시각 1개로 압축하지 않고, distinct
  // fcstTime마다 슬롯 DTO를 그대로 만든다. now 파라미터가 없다 - 어느 슬롯도 "지금과 가장
  // 가까운 슬롯"으로 선별되지 않고 응답에 있는 슬롯을 전부 반환하기 때문이다.
  public List<WeatherForecastSlotDto> parseSlotForecast(KmaWeatherResponse response) {
    List<Item> allItems = response.response().body().items().item();
    String baseDate = allItems.isEmpty() ? null : allItems.get(0).baseDate();
    TreeMap<String, List<Item>> itemsByDate = allItems.stream()
        .collect(Collectors.groupingBy(Item::fcstDate, TreeMap::new, Collectors.toList()));
    // fcstDate가 "yyyyMMdd" 고정 폭 문자열이라 사전순 정렬이 곧 날짜순 정렬이다.
    String lastFcstDate = itemsByDate.isEmpty() ? null : itemsByDate.lastKey();

    List<WeatherForecastSlotDto> result = new ArrayList<>();
    for (Map.Entry<String, List<Item>> entry : itemsByDate.entrySet()) {
      String fcstDate = entry.getKey();
      List<Item> dayItems = entry.getValue();
      if (!hasTemperatureData(dayItems)) {
        continue;
      }
      // 끝자락(응답에 있는 가장 마지막) 날짜만 완결성을 확인한다 - base_date(오늘)는 원래도
      // 남은 시간만 오는 게 정상이라 제외하고, D1~D3처럼 중간 날짜는 이 검증 대상이 아니다.
      if (fcstDate.equals(lastFcstDate) && !fcstDate.equals(baseDate)
          && !hasFullDayGrid(dayItems)) {
        log.warn("끝자락 날짜 슬롯 미달로 통째로 제외: fcstDate={}, 슬롯수={}", fcstDate,
            dayItems.stream().map(Item::fcstTime).distinct().count());
        continue;
      }
      LocalDate date = LocalDate.parse(fcstDate, DATE_FORMATTER);
      DailyTemperatureRange range = temperatureRange(dayItems);
      DailyPrecipitationSummary precipitationSummary = precipitationSummary(dayItems);
      DailyCategoricalSummary categorical = categoricalSummary(dayItems);
      Map<String, List<Item>> itemsBySlot = dayItems.stream()
          .collect(Collectors.groupingBy(Item::fcstTime, TreeMap::new, Collectors.toList()));
      for (Map.Entry<String, List<Item>> slot : itemsBySlot.entrySet()) {
        toSlotForecast(date, slot.getKey(), slot.getValue(), range, precipitationSummary,
            categorical).ifPresent(result::add);
      }
    }
    return result;
  }

  // TMN(일 최저기온)/TMX(일 최고기온)이 응답에 있으면 그 값을 우선하고, 없는 값만 그 날의
  // TMP(시간별 기온) 극값으로 대체한다.
  private DailyTemperatureRange temperatureRange(List<Item> dayItems) {
    double tmpMin = Double.MAX_VALUE;
    double tmpMax = -Double.MAX_VALUE;
    Double tmn = null;
    Double tmx = null;
    for (Item item : dayItems) {
      switch (item.category()) {
        case "TMP" -> {
          double value = Double.parseDouble(item.fcstValue());
          tmpMin = Math.min(tmpMin, value);
          tmpMax = Math.max(tmpMax, value);
        }
        case "TMN" -> tmn = Double.parseDouble(item.fcstValue());
        case "TMX" -> tmx = Double.parseDouble(item.fcstValue());
        default -> {
        }
      }
    }
    return new DailyTemperatureRange(tmn != null ? tmn : tmpMin, tmx != null ? tmx : tmpMax);
  }

  // POP(강수확률)은 "그날 비 올 가능성"(하루 최댓값), PCP(강수량)는 "그날 강수량"(하루 합계)이라는
  // 기존 의미를 유지한다 - temperatureRange()와 같은 자리에서 날짜 단위로 한 번만 계산해 그날 모든
  // 슬롯에 동일하게 적용한다.
  private DailyPrecipitationSummary precipitationSummary(List<Item> dayItems) {
    double probabilityMax = 0.0;
    double amountSum = 0.0;
    for (Item item : dayItems) {
      switch (item.category()) {
        case "POP" ->
            probabilityMax = Math.max(probabilityMax, Double.parseDouble(item.fcstValue()));
        case "PCP" -> amountSum += parsePrecipitationAmount(item.fcstValue());
        default -> {
        }
      }
    }
    return new DailyPrecipitationSummary(probabilityMax, amountSum);
  }

  // temperatureRange()/precipitationSummary()와 같은 자리 - 그날 슬롯 전체를 훑어 하늘상태는
  // 가장 안 좋은 값, 강수형태는 최다 등장값, 습도는 최댓값으로 계산해 그날 모든 슬롯에 동일하게
  // 적용한다.
  private DailyCategoricalSummary categoricalSummary(List<Item> dayItems) {
    SkyStatus worstSky = SkyStatus.CLEAR;
    double maxHumidity = 0.0;
    Map<PrecipitationType, Long> precipitationTypeCounts = new EnumMap<>(PrecipitationType.class);

    for (Item item : dayItems) {
      switch (item.category()) {
        case "SKY" -> {
          SkyStatus sky = toSkyStatus(item.fcstValue());
          if (sky.ordinal() > worstSky.ordinal()) {
            worstSky = sky;
          }
        }
        case "REH" -> maxHumidity = Math.max(maxHumidity, Double.parseDouble(item.fcstValue()));
        case "PTY" -> {
          String pty = item.fcstValue();
          PrecipitationType type = (pty == null || "0".equals(pty))
              ? PrecipitationType.NONE : toPrecipitationType(pty);
          precipitationTypeCounts.merge(type, 1L, Long::sum);
        }
        default -> {
        }
      }
    }
    return new DailyCategoricalSummary(worstSky, precipitationTypeMode(precipitationTypeCounts),
        maxHumidity);
  }

  // 최다 등장값 - 동률이면 enum 선언 순서상 나중 값(ordinal이 큰 쪽) 우선.
  private PrecipitationType precipitationTypeMode(Map<PrecipitationType, Long> counts) {
    return counts.entrySet().stream()
        .max(Comparator.<Map.Entry<PrecipitationType, Long>>comparingLong(Map.Entry::getValue)
            .thenComparing(entry -> entry.getKey().ordinal()))
        .map(Map.Entry::getKey)
        .orElse(PrecipitationType.NONE);
  }

  private Optional<WeatherForecastSlotDto> toSlotForecast(LocalDate date, String slotTime,
      List<Item> slotItems, DailyTemperatureRange range,
      DailyPrecipitationSummary precipitationSummary, DailyCategoricalSummary categorical) {
    Double tempCurrent = null;
    double humidityCurrent = 0.0;
    double windSpeed = 0.0;
    SkyStatus skyStatus = SkyStatus.CLEAR;
    PrecipitationType precipitationType = PrecipitationType.NONE;

    for (Item item : slotItems) {
      switch (item.category()) {
        case "TMP" -> tempCurrent = Double.parseDouble(item.fcstValue());
        case "SKY" -> skyStatus = toSkyStatus(item.fcstValue());
        case "REH" -> humidityCurrent = Double.parseDouble(item.fcstValue());
        case "WSD" -> windSpeed = Double.parseDouble(item.fcstValue());
        case "PTY" -> {
          String pty = item.fcstValue();
          if (pty != null && !"0".equals(pty)) {
            precipitationType = toPrecipitationType(pty);
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
        precipitationType, precipitationSummary.amountSum(), precipitationSummary.probabilityMax(),
        humidityCurrent, tempCurrent, range.min(), range.max(), windSpeed,
        categorical.skyStatusWorst(), categorical.precipitationTypeMode(),
        categorical.humidityMax()));
  }

  private Instant toInstant(LocalDate date, String slotTime) {
    int hour = Integer.parseInt(slotTime.substring(0, 2));
    int minute = Integer.parseInt(slotTime.substring(2, 4));
    return date.atTime(hour, minute).atZone(KST).toInstant();
  }

  private record DailyTemperatureRange(double min, double max) {

  }

  private record DailyPrecipitationSummary(double probabilityMax, double amountSum) {

  }

  private record DailyCategoricalSummary(SkyStatus skyStatusWorst,
      PrecipitationType precipitationTypeMode, double humidityMax) {

  }

  private boolean hasTemperatureData(List<Item> dayItems) {
    return dayItems.stream().anyMatch(item -> "TMP".equals(item.category()));
  }

  private boolean hasFullDayGrid(List<Item> dayItems) {
    long distinctSlotCount = dayItems.stream().map(Item::fcstTime).distinct().count();
    return distinctSlotCount >= FULL_DAY_SLOT_COUNT;
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