package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherD1BaselineCustomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherD1BaselineRepository
    extends JpaRepository<WeatherD1Baseline, UUID>, WeatherD1BaselineCustomRepository {

  // D1 급변 알림 청크 배치 전용(#163) - 청크 안 그리드 전체의 baseline을 그리드별 개별 조회
  // 대신 IN 절 하나로 묶어 쿼리 1번에 끝낸다.
  List<WeatherD1Baseline> findAllByWeatherGridIdInAndTargetDate(List<UUID> weatherGridIds,
      LocalDate targetDate);
}