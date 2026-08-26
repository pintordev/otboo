package com.sprint.mission.otboo.batch.feedreindex.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedReindexReader")
class FeedReindexReaderTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  private FeedReindexReader reader;

  @Mock
  private FeedRepository feedRepository;

  private static Feed feedWith(UUID id, Instant createdAt, String content) {
    Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), content,
        DUMMY_SNAPSHOT, List.of());
    setField(feed, "id", id);
    setField(feed, "createdAt", createdAt);
    return feed;
  }

  private static void setField(Feed feed, String name, Object value) {
    try {
      var field = Feed.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(feed, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setUp() {
    reader = new FeedReindexReader(feedRepository,
        new FeedReindexProperties(2, 10, Duration.ofHours(2)));
  }

  @Nested
  @DisplayName("재색인 대상 조회")
  class Read {

    @Test
    @DisplayName("커서 기반으로 페이지 단위로 순차 조회하고 소진되면 null을 반환한다")
    void 커서_기반으로_페이지_단위로_순차_조회하고_소진되면_null을_반환한다() {
      // given
      Feed feed1 = feedWith(UUID.randomUUID(), Instant.parse("2026-08-20T01:00:00Z"), "피드1");
      Feed feed2 = feedWith(UUID.randomUUID(), Instant.parse("2026-08-20T02:00:00Z"), "피드2");
      given(feedRepository.findForReindex(any(), any(), anyInt()))
          .willReturn(List.of(feed1, feed2), List.of());

      // when
      Feed r1 = reader.read();
      Feed r2 = reader.read();
      Feed r3 = reader.read();

      // then
      assertThat(r1).isEqualTo(feed1);
      assertThat(r2).isEqualTo(feed2);
      assertThat(r3).isNull();
    }

    @Test
    @DisplayName("직전 항목의 createdAt id를 커서로 다음 페이지를 조회한다")
    void 직전_항목의_createdAt_id를_커서로_다음_페이지를_조회한다() {
      // given
      UUID id1 = UUID.randomUUID();
      Instant createdAt1 = Instant.parse("2026-08-20T01:00:00Z");
      Feed feed1 = feedWith(id1, createdAt1, "피드1");
      Feed feed2 = feedWith(UUID.randomUUID(), Instant.parse("2026-08-20T02:00:00Z"), "피드2");
      given(feedRepository.findForReindex(any(), any(), anyInt()))
          .willReturn(List.of(feed1), List.of(feed2), List.of());

      // when
      reader.read();
      reader.read();

      // then
      ArgumentCaptor<Instant> createdAtCaptor = ArgumentCaptor.forClass(Instant.class);
      ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
      verify(feedRepository, times(2))
          .findForReindex(createdAtCaptor.capture(), idCaptor.capture(), anyInt());

      assertThat(createdAtCaptor.getAllValues().get(0)).isEqualTo(Instant.EPOCH);
      assertThat(createdAtCaptor.getAllValues().get(1)).isEqualTo(createdAt1);
      assertThat(idCaptor.getAllValues().get(1)).isEqualTo(id1);
    }
  }
}
