package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 클래스 레벨 @Transactional(readOnly = true)를 의도적으로 두지 않는다 - API 경로뿐 아니라
// 배치(WeatherFetchProcessor) 청크 트랜잭션 안에서도 호출되는데, 그 안에서 readOnly로
// 참여하면 배치가 관리하는 flush/커밋 타이밍과 충돌해 정상 처리된 항목이 유실된다(실측 확인).
// 쓰기가 필요한 지점(WeatherWriter.save())은 이미 자체 REQUIRES_NEW를 갖고 있고, 읽기 쿼리는
// Spring Data JPA repository 메서드 자체가 개별적으로 트랜잭션을 처리하므로 감싸는 애너테이션이
// 없어도 정상 동작한다.
@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherRefresher {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final KmaForecastFetcher kmaForecastFetcher;
  private final WeatherWriter weatherWriter;
  private final Clock clock;

  public List<Weather> refresh(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    Fetched fetched = fetch(weatherGrid, grid, baseTime);
    List<Weather> saved = weatherWriter.save(weatherGrid, baseTime.toInstant(),
        fetched.dailyForecasts(), fetched.existingByDate());
    log.info("기상청 라이브 재조회 저장 완료: nx={}, ny={}, 저장 건수={}", grid.nx(), grid.ny(), saved.size());
    return saved;
  }

  public List<Weather> build(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    Fetched fetched = fetch(weatherGrid, grid, baseTime);
    return weatherWriter.build(weatherGrid, baseTime.toInstant(), fetched.dailyForecasts(),
        fetched.existingByDate());
  }

  private Fetched fetch(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    LocalDate yesterday = LocalDate.now(clock.withZone(KST)).minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    Map<LocalDate, Weather> existingByDate = weatherRepository.findLatestRevisions(weatherGrid,
            from)
        .stream()
        .collect(Collectors.toMap(this::toForecastDate, w -> w));

    log.info("기상청 라이브 재조회: nx={}, ny={}, baseDate={}, baseTime={}", grid.nx(), grid.ny(),
        baseTime.baseDate(), baseTime.baseTime());
    List<DailyWeatherForecastDto> dailyForecasts = kmaForecastFetcher.fetch(grid, baseTime,
        clock.instant());
    return new Fetched(dailyForecasts, existingByDate);
  }

  private LocalDate toForecastDate(Weather weather) {
    return weather.getForecastAt().atZone(KST).toLocalDate();
  }

  private record Fetched(List<DailyWeatherForecastDto> dailyForecasts,
      Map<LocalDate, Weather> existingByDate) {

  }
}