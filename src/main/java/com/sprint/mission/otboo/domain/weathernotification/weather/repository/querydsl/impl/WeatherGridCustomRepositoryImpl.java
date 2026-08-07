package com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.weathernotification.weather.entity.QWeather.weather;
import static com.sprint.mission.otboo.domain.weathernotification.weather.entity.QWeatherGrid.weatherGrid;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.querydsl.WeatherGridCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WeatherGridCustomRepositoryImpl implements WeatherGridCustomRepository {

  // limit이 지나치게 크게 들어와도 한 번의 조회로 대량 데이터를 끌어오지 않도록 방어적으로 상한을 둔다
  private static final int MAX_LIMIT = 1000;

  private final JPAQueryFactory queryFactory;

  @Override
  public List<WeatherGrid> findPageByCursor(Instant lastCreatedAt, UUID lastId, int limit) {
    return queryFactory
        .selectFrom(weatherGrid)
        .where(cursorCondition(lastCreatedAt, lastId))
        .orderBy(weatherGrid.createdAt.asc(), weatherGrid.id.asc())
        .limit(clampLimit(limit))
        .fetch();
  }

  @Override
  public List<WeatherGrid> findPageByCursorExcludingForecasted(Instant lastCreatedAt, UUID lastId,
      Instant forecastedAt, int limit) {
    return queryFactory
        .selectFrom(weatherGrid)
        .where(cursorCondition(lastCreatedAt, lastId), notForecasted(forecastedAt))
        .orderBy(weatherGrid.createdAt.asc(), weatherGrid.id.asc())
        .limit(clampLimit(limit))
        .fetch();
  }

  private int clampLimit(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit은 1 이상이어야 합니다: " + limit);
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private BooleanExpression cursorCondition(Instant lastCreatedAt, UUID lastId) {
    return weatherGrid.createdAt.gt(lastCreatedAt)
        .or(weatherGrid.createdAt.eq(lastCreatedAt).and(weatherGrid.id.gt(lastId)));
  }

  private BooleanExpression notForecasted(Instant forecastedAt) {
    return JPAExpressions.selectOne()
        .from(weather)
        .where(weather.weatherGrid.eq(weatherGrid), weather.forecastedAt.eq(forecastedAt))
        .notExists();
  }
}