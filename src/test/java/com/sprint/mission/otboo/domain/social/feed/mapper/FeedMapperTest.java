package com.sprint.mission.otboo.domain.social.feed.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeedMapper")
class FeedMapperTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .build();

  FeedMapper feedMapper = new FeedMapper();

  @Nested
  @DisplayName("toDto 변환")
  class ToDto {

    @Test
    @DisplayName("Feed 엔티티를 FeedDto로 변환하고 likedByMe를 전달값으로 채운다")
    void Feed_엔티티를_FeedDto로_변환하고_likedByMe를_전달값으로_채운다() {
      // given
      Feed feed = fm.giveMeBuilder(Feed.class)
          .set("content", "오늘의 착장")
          .set("likeCount", 0L)
          .set("commentCount", 0)
          .sample();

      // when
      FeedDto result = feedMapper.toDto(feed, false);

      // then
      assertThat(result.content()).isEqualTo("오늘의 착장");
      assertThat(result.likeCount()).isZero();
      assertThat(result.commentCount()).isZero();
      assertThat(result.likedByMe()).isFalse();
    }
  }
}