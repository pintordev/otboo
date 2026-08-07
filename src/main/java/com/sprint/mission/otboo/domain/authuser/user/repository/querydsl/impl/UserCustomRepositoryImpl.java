package com.sprint.mission.otboo.domain.authuser.user.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.authuser.user.entity.QUser.user;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.authuser.user.dto.request.UserListParams;
import com.sprint.mission.otboo.domain.authuser.user.dto.response.UserDto;
import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Role;
import com.sprint.mission.otboo.domain.authuser.user.repository.querydsl.UserCustomRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class UserCustomRepositoryImpl implements UserCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorPageResponse<UserDto> search(UserListParams condition) {

    List<UserDto> content = queryFactory
        .select(
            Projections.constructor(UserDto.class,
                user.id,
                user.createdAt,
                user.email,
                user.name,
                user.role,
                user.locked
            )
        )
        .from(user)
        .where(
            emailLikeCondition(condition.emailLike()),
            roleEqualCondition(condition.roleEqual()),
            lockedCondition(condition.locked()),
            cursorCondition(condition)
        )
        .orderBy(orderSpecifiers(condition))
        .limit(condition.limit() + 1)
        .fetch();

    boolean hasNext = content.size() > condition.limit();
    List<UserDto> data = hasNext ? content.subList(0, condition.limit()) : content;

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !data.isEmpty()) {
      UserDto last = data.get(data.size() - 1);
      nextCursor = extractCursor(last, condition.sortBy());
      nextIdAfter = last.id();
    }

    long totalCount = Optional.ofNullable(
        queryFactory
            .select(user.count())
            .from(user)
            .where(
                emailLikeCondition(condition.emailLike()),
                roleEqualCondition(condition.roleEqual()),
                lockedCondition(condition.locked())
            )
            .fetchOne()
    ).orElse(0L);

    return new CursorPageResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        condition.sortBy(),
        condition.sortDirection()
    );
  }

  private String extractCursor(UserDto last, String sortBy) {
    return "email".equals(sortBy) ? last.email() : last.createdAt().toString();
  }

  private BooleanExpression emailLikeCondition(String emailLike) {
    return StringUtils.hasText(emailLike) ? user.email.containsIgnoreCase(emailLike) : null;
  }

  private BooleanExpression roleEqualCondition(Role roleEqual) {
    return roleEqual != null ? user.role.eq(roleEqual) : null;
  }

  private BooleanExpression lockedCondition(Boolean locked) {
    return locked != null ? user.locked.eq(locked) : null;
  }

  private BooleanExpression cursorCondition(UserListParams condition) {

    if (condition.cursor() == null) {
      return null; // 첫 페이지 요청
    }

    boolean ascending = condition.sortDirection() == SortDirection.ASCENDING;

    if ("email".equals(condition.sortBy())) {

      if (ascending) {
        // 오름차순
        return user.email.gt(condition.cursor())
            .or(user.email.eq(condition.cursor()).and(user.id.gt(condition.idAfter())));
      } else {
        // 내림차순
        return user.email.lt(condition.cursor())
            .or(user.email.eq(condition.cursor()).and(user.id.lt(condition.idAfter())));
      }

    } else {

      Instant cursorInstant = Instant.parse(condition.cursor());

      if (ascending) {
        // 오름차순
        return user.createdAt.gt(cursorInstant)
            .or(user.createdAt.eq(cursorInstant).and(user.id.gt(condition.idAfter())));
      } else {
        return user.createdAt.lt(cursorInstant)
            .or(user.createdAt.eq(cursorInstant).and(user.id.lt(condition.idAfter())));
      }
    }
  }

  private OrderSpecifier<?>[] orderSpecifiers(UserListParams condition) {

    String sortBy = condition.sortBy();
    boolean ascending = condition.sortDirection() == SortDirection.ASCENDING;

    OrderSpecifier<?> primary = "email".equals(sortBy)
        ? (ascending ? user.email.asc() : user.email.desc())
        : (ascending ? user.createdAt.asc() : user.createdAt.desc());

    OrderSpecifier<?> tieBreaker = ascending ? user.id.asc() : user.id.desc();

    return new OrderSpecifier<?>[]{primary, tieBreaker};
  }
}
