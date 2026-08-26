package com.sprint.mission.otboo.batch.feedreindex.listener;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeedReindexSkipListener")
class FeedReindexSkipListenerTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);
  private final FeedReindexSkipListener listener = new FeedReindexSkipListener();

  @Nested
  @DisplayName("쓰기 실패 기록")
  class OnSkipInWrite {

    @Test
    @DisplayName("예외가 나도 전파하지 않는다")
    void 예외가_나도_전파하지_않는다() {
      // given
      Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "피드",
          DUMMY_SNAPSHOT, List.of());

      // when & then
      assertThatCode(() -> listener.onSkipInWrite(feed, new RuntimeException("실패")))
          .doesNotThrowAnyException();
    }
  }
}
