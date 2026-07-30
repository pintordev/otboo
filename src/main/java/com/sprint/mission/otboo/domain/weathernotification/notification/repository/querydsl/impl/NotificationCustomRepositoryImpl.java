package com.sprint.mission.otboo.domain.weathernotification.notification.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.weathernotification.notification.entity.QNotification.notification;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.querydsl.NotificationCustomRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotificationCustomRepositoryImpl implements NotificationCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorPageResponse<NotificationDto> findNotifications(
      UUID receiverId, NotificationListParams params) {
    List<NotificationDto> raw = fetchNotifications(receiverId, params);

    boolean hasNext = raw.size() > params.limit();
    List<NotificationDto> data = hasNext ? raw.subList(0, params.limit()) : raw;

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !data.isEmpty()) {
      NotificationDto last = data.get(data.size() - 1);
      nextCursor = last.createdAt().toString();
      nextIdAfter = last.id();
    }

    return new CursorPageResponse<>(
        data, nextCursor, nextIdAfter, hasNext, countNotifications(receiverId),
        "createdAt", SortDirection.DESCENDING);
  }

  private List<NotificationDto> fetchNotifications(UUID receiverId, NotificationListParams params) {
    return queryFactory
        .select(Projections.constructor(
            NotificationDto.class,
            notification.id,
            notification.createdAt,
            notification.receiverId,
            notification.title,
            notification.content,
            notification.level
        ))
        .from(notification)
        .where(
            notification.receiverId.eq(receiverId),
            cursorCondition(params)
        )
        .orderBy(createdAtOrderSpecifier(), idOrderSpecifier())
        .limit(params.limit() + 1L)
        .fetch();
  }

  private long countNotifications(UUID receiverId) {
    return Optional.ofNullable(
        queryFactory.select(notification.count())
            .from(notification)
            .where(notification.receiverId.eq(receiverId))
            .fetchOne()
    ).orElse(0L);
  }

  private BooleanExpression cursorCondition(NotificationListParams params) {
    if (params.cursor() == null) {
      return null;
    }
    Instant cursorTime = Instant.parse(params.cursor());
    return notification.createdAt.lt(cursorTime)
        .or(notification.createdAt.eq(cursorTime).and(notification.id.lt(params.idAfter())));
  }

  private OrderSpecifier<Instant> createdAtOrderSpecifier() {
    return new OrderSpecifier<>(Order.DESC, notification.createdAt);
  }

  private OrderSpecifier<UUID> idOrderSpecifier() {
    return new OrderSpecifier<>(Order.DESC, notification.id);
  }
}