package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.weathernotification.weather.entity.QWeather.weather;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WeatherCustomRepositoryImpl implements WeatherCustomRepository {

  // WeatherGridCustomRepositoryImpl과 동일한 방어적 상한
  private static final int MAX_LIMIT = 1000;

  private final JPAQueryFactory queryFactory;

  @Override
  public List<WeatherRetentionItem> findForRetention(Instant cutoff, Instant lastForecastAt,
      UUID lastId, int limit) {
    return queryFactory
        .select(Projections.constructor(WeatherRetentionItem.class,
            weather.id, weather.forecastAt))
        .from(weather)
        .where(weather.forecastAt.lt(cutoff), cursorCondition(lastForecastAt, lastId))
        .orderBy(weather.forecastAt.asc(), weather.id.asc())
        .limit(clampLimit(limit))
        .fetch();
  }

  private int clampLimit(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit은 1 이상이어야 합니다: " + limit);
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private BooleanExpression cursorCondition(Instant lastForecastAt, UUID lastId) {
    return weather.forecastAt.gt(lastForecastAt)
        .or(weather.forecastAt.eq(lastForecastAt).and(weather.id.gt(lastId)));
  }
}