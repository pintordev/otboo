package com.sprint.mission.otboo.domain.social.feed.repository;

import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.querydsl.FeedCustomRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, UUID>, FeedCustomRepository {

}