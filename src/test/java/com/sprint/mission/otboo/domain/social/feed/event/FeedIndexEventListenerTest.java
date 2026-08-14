package com.sprint.mission.otboo.domain.social.feed.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexEventListener")
class FeedIndexEventListenerTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @InjectMocks
  FeedIndexEventListener listener;

  @Mock
  FeedRepository feedRepository;

  @Mock
  FeedSearchRepository feedSearchRepository;

  private static void setFeedId(Feed feed, UUID id) {
    try {
      var field = Feed.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(feed, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  @DisplayName("인덱싱 이벤트 처리")
  class Handle {

    @Test
    @DisplayName("UPSERT 이벤트를 받으면 피드를 조회해 인덱스에 저장한다")
    void UPSERT_이벤트를_받으면_피드를_조회해_인덱스에_저장한다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "오늘의 착장",
          DUMMY_SNAPSHOT, List.of());
      setFeedId(feed, feedId);
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      listener.handle(FeedIndexRequestedEvent.upsert(feedId));

      // then
      ArgumentCaptor<FeedDocument> captor = ArgumentCaptor.forClass(FeedDocument.class);
      verify(feedSearchRepository).save(captor.capture());
      assertThat(captor.getValue().getId()).isEqualTo(feedId.toString());
      assertThat(captor.getValue().getContent()).isEqualTo("오늘의 착장");
    }

    @Test
    @DisplayName("DELETE 이벤트를 받으면 인덱스에서 제거한다")
    void DELETE_이벤트를_받으면_인덱스에서_제거한다() {
      // given
      UUID feedId = UUID.randomUUID();

      // when
      listener.handle(FeedIndexRequestedEvent.delete(feedId));

      // then
      verify(feedSearchRepository).deleteById(feedId.toString());
      verify(feedRepository, never()).findById(any());
    }

    @Test
    @DisplayName("인덱싱에 실패해도 예외를 던지지 않는다")
    void 인덱싱에_실패해도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      given(feedRepository.findById(feedId))
          .willThrow(new RuntimeException("ES 연결 실패"));

      // when & then
      assertThatCode(() -> listener.handle(FeedIndexRequestedEvent.upsert(feedId)))
          .doesNotThrowAnyException();
    }
  }
}
