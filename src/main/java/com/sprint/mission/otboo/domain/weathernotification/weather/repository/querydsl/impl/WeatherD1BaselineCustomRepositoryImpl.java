package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.weathernotification.weather.entity.QWeatherD1Baseline.weatherD1Baseline;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherD1BaselineCustomRepository;
import com.sprint.mission.otboo.global.batch.BatchConstants;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WeatherD1BaselineCustomRepositoryImpl implements WeatherD1BaselineCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<WeatherD1BaselineRetentionItem> findForRetention(LocalDate cutoff,
      LocalDate lastTargetDate, UUID lastId, int limit) {
    return queryFactory
        .select(Projections.constructor(WeatherD1BaselineRetentionItem.class,
            weatherD1Baseline.id, weatherD1Baseline.targetDate))
        .from(weatherD1Baseline)
        .where(weatherD1Baseline.targetDate.loe(cutoff), cursorCondition(lastTargetDate, lastId))
        .orderBy(weatherD1Baseline.targetDate.asc(), weatherD1Baseline.id.asc())
        .limit(clampLimit(limit))
        .fetch();
  }

  private int clampLimit(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit은 1 이상이어야 합니다: " + limit);
    }
    return Math.min(limit, BatchConstants.MAX_CHUNK_SIZE);
  }

  private BooleanExpression cursorCondition(LocalDate lastTargetDate, UUID lastId) {
    return weatherD1Baseline.targetDate.gt(lastTargetDate)
        .or(weatherD1Baseline.targetDate.eq(lastTargetDate).and(weatherD1Baseline.id.gt(lastId)));
  }
}