package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRepository extends JpaRepository<Weather, UUID>, WeatherCustomRepository {

  Optional<Weather> findByWeatherGridAndForecastAtAndForecastedAt(WeatherGrid weatherGrid,
      Instant forecastAt, Instant forecastedAt);

  // findLatestRevisions()의 슬롯 단위 대체 - V14(유니크 제약 (weather_grid_id, forecast_at))
  // 이후로는 forecast_at당 row가 정확히 1개라 "최신 리비전 골라내기"(DISTINCT ON) 자체가 필요
  // 없다. WeatherService/WeatherRefresher가 갈아탄 뒤 findLatestRevisions는 삭제 완료.
  List<Weather> findAllByWeatherGridAndForecastAtGreaterThanEqual(WeatherGrid weatherGrid,
      Instant from);

  // D1 급변 알림의 D2 스냅샷 캡처 전용(#163) - target_date 하루치(24시간) 슬롯만 조회한다.
  List<Weather> findAllByWeatherGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      WeatherGrid weatherGrid, Instant from, Instant to);

  // saveSlots()가 upsert 후 현재 DB 상태를 되돌려 받는 용도(#243) - forecastedAt으로 필터링하지
  // 않는다. upsert 역행 방지 가드에 걸려 이번 호출의 forecastedAt으로 갱신되지 않은 슬롯도
  // 결과에 포함시켜야, 호출부가 그 슬롯을 "재조회 실패"로 오판해 다음 요청에서도 반복 재조회하지
  // 않는다.
  List<Weather> findAllByWeatherGridAndForecastAtInOrderByForecastAt(
      WeatherGrid weatherGrid, List<Instant> forecastAts);

  // D0/D1 급변 알림 청크 배치 전용(#163) - 청크 안 그리드 전체의 D0/D1 대상 날짜 24시간 슬롯을
  // 그리드별 개별 조회 대신 IN 절 하나로 묶어 쿼리 1번에 끝낸다.
  List<Weather> findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      List<UUID> weatherGridIds, Instant from, Instant to);

  @Query("SELECT DISTINCT w.weatherGrid FROM Weather w WHERE w.forecastedAt = :forecastedAt")
  List<WeatherGrid> findGridsUpdatedAt(@Param("forecastedAt") Instant forecastedAt);

  // 급변 알림 D0 발행 후 baseline 리셋 전용(#163) - baseline_* 컬럼만 갱신, current 컬럼은 건드리지 않는다.
  @Modifying
  @Query("UPDATE Weather w SET w.baselineTemperatureCurrent = :temperatureCurrent, "
      + "w.baselinePrecipitationType = :precipitationType, "
      + "w.baselinePrecipitationProbability = :precipitationProbability, "
      + "w.baselinePrecipitationAmount = :precipitationAmount WHERE w.id = :id")
  void updateBaseline(@Param("id") UUID id,
      @Param("temperatureCurrent") double temperatureCurrent,
      @Param("precipitationType") PrecipitationType precipitationType,
      @Param("precipitationProbability") double precipitationProbability,
      @Param("precipitationAmount") double precipitationAmount);
}