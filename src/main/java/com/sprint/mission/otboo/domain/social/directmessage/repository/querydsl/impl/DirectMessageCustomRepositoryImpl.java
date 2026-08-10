package com.sprint.mission.otboo.domain.social.directmessage.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.social.directmessage.entity.QDirectMessage.directMessage;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.social.directmessage.dto.DirectMessageParams;
import com.sprint.mission.otboo.domain.social.directmessage.entity.DirectMessage;
import com.sprint.mission.otboo.domain.social.directmessage.repository.querydsl.DirectMessageCustomRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class DirectMessageCustomRepositoryImpl implements DirectMessageCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorPageResponse<DirectMessage> findDirectMessages(UUID currentUserId,
      DirectMessageParams params) {
    List<DirectMessage> raw = fetchDirectMessages(currentUserId, params);

    boolean hasNext = raw.size() > params.limit();
    List<DirectMessage> page = hasNext ? raw.subList(0, params.limit()) : raw;

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !page.isEmpty()) {
      DirectMessage last = page.get(page.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    return new CursorPageResponse<>(page, nextCursor, nextIdAfter, hasNext,
        countDirectMessages(currentUserId, params.userId()), "createdAt", SortDirection.DESCENDING
    );
  }

  private List<DirectMessage> fetchDirectMessages(UUID currentUserId,
      DirectMessageParams params) {
    return queryFactory
        .selectFrom(directMessage)
        .where(
            betweenUsers(currentUserId, params.userId()),
            cursorCondition(params.cursor(), params.idAfter())
        )
        .orderBy(directMessage.createdAt.desc(), directMessage.id.desc())
        .limit(params.limit() + 1L)
        .fetch();
  }

  private long countDirectMessages(UUID currentUserId, UUID otherUserId) {
    return Optional.ofNullable(
        queryFactory.select(directMessage.count())
            .from(directMessage)
            .where(betweenUsers(currentUserId, otherUserId))
            .fetchOne()
    ).orElse(0L);
  }

  // 발신/수신 방향 모두 포함
  private BooleanExpression betweenUsers(UUID one, UUID other) {
    return directMessage.senderId.eq(one).and(directMessage.receiverId.eq(other))
        .or(directMessage.senderId.eq(other).and(directMessage.receiverId.eq(one)));
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    Instant instant = Instant.parse(cursor);
    return directMessage.createdAt.lt(instant)
        .or(directMessage.createdAt.eq(instant).and(directMessage.id.lt(idAfter)));
  }
}