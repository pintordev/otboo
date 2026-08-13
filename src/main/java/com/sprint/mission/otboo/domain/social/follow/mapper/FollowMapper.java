package com.sprint.mission.otboo.domain.social.follow.mapper;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowDto;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowSummaryDto;
import com.sprint.mission.otboo.domain.social.follow.entity.Follow;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FollowMapper {

  public FollowDto toDto(Follow follow, UserSummary follower, UserSummary followee) {
    return toDto(follow, Map.of(
        follower.userId(), follower,
        followee.userId(), followee));
  }

  public FollowDto toDto(Follow follow, Map<UUID, UserSummary> summaryMap) {
    return new FollowDto(follow.getId(),
        summaryMap.get(follow.getFolloweeId()),
        summaryMap.get(follow.getFollowerId()));
  }

  public FollowSummaryDto toSummaryDto(UUID followeeId, long followerCount, long followingCount,
      boolean followedByMe, UUID followedByMeId, boolean followingMe
  ) {
    return new FollowSummaryDto(
        followeeId, followerCount, followingCount, followedByMe, followedByMeId, followingMe);
  }
}
