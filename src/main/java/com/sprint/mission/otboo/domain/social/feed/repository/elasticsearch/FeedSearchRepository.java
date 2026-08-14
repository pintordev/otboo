package com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FeedSearchRepository extends ElasticsearchRepository<FeedDocument, String> {

}
