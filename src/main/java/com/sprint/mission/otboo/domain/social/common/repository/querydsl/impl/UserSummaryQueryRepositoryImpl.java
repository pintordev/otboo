package com.sprint.mission.otboo.domain.social.common.repository.querydsl.impl;

import static com.sprint.mission.otboo.domain.authuser.user.entity.QProfile.profile;
import static com.sprint.mission.otboo.domain.authuser.user.entity.QUser.user;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserSummaryQueryRepositoryImpl implements UserSummaryQueryRepository {

  private final JPAQueryFactory queryFactory;

  public UserSummary findByUserId(UUID userId) {
    UserSummary result = queryFactory
        .select(Projections.constructor(UserSummary.class,
            user.id, user.name, profile.profileImageUrl))
        .from(user)
        .leftJoin(profile).on(profile.id.eq(user.id))
        .where(user.id.eq(userId))
        .fetchOne();

    if (result == null) {
      throw UserNotFoundException.withNone();
    }
    return result;
  }

  @Override
  public boolean existsByUserId(UUID userId) {
    Integer fetchOne = queryFactory
        .selectOne()
        .from(user)
        .where(user.id.eq(userId))
        .fetchFirst();

    return fetchOne != null;
  }

  public List<UserSummary> findByUserIds(Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }

    return queryFactory
        .select(Projections.constructor(UserSummary.class,
            user.id, user.name, profile.profileImageUrl))
        .from(user)
        .leftJoin(profile).on(profile.id.eq(user.id))
        .where(user.id.in(userIds))
        .fetch();
  }
}