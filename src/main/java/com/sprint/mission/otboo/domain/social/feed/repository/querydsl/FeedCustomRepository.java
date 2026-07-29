package com.sprint.mission.otboo.domain.social.feed.repository.querydsl;

import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;

public interface FeedCustomRepository {

  CursorPageResponse<Feed> findFeeds(FeedListParams params);
}