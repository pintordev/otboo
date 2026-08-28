package com.sprint.mission.otboo.domain.social.feed.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import java.time.Instant;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexEventListener")
class FeedIndexEventListenerTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);
  static final Instant FIXED_TIME = Instant.parse("2026-08-20T01:00:00Z");

  @InjectMocks
  FeedIndexEventListener listener;

  @Mock
  FeedRepository feedRepository;

  @Mock
  FeedSearchRepository feedSearchRepository;

  @Mock
  ElasticsearchOperations elasticsearchOperations;

  private static void setField(Feed feed, String name, Object value) {
    try {
      var field = Feed.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(feed, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Feed feedWith(UUID id, String content) {
    Feed feed = Feed.create(UUID.randomUUID(), UUID.randomUUID(), content,
        DUMMY_SNAPSHOT, List.of());
    setField(feed, "id", id);
    setField(feed, "createdAt", FIXED_TIME);
    setField(feed, "updatedAt", FIXED_TIME);
    return feed;
  }

  @Nested
  @DisplayName("인덱싱 이벤트 처리")
  class Handle {

    @Test
    @DisplayName("UPSERT 이벤트를 받으면 피드를 조회해 인덱스에 저장한다")
    void UPSERT_이벤트를_받으면_피드를_조회해_인덱스에_저장한다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
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
    @DisplayName("피드 조회에 실패해도 예외를 던지지 않는다")
    void 피드_조회에_실패해도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      given(feedRepository.findById(feedId))
          .willThrow(new DataAccessResourceFailureException("DB 연결 실패"));

      // when & then
      assertThatCode(() -> listener.handle(FeedIndexRequestedEvent.upsert(feedId)))
          .doesNotThrowAnyException();
      verify(feedRepository).findById(feedId);
      verify(feedSearchRepository, never()).save(any());
      verify(feedSearchRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("인덱스 저장에 실패해도 예외를 던지지 않는다")
    void 인덱스_저장에_실패해도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));
      willThrow(new DataAccessResourceFailureException("ES 연결 실패"))
          .given(feedSearchRepository).save(any(FeedDocument.class));

      // when & then
      assertThatCode(() -> listener.handle(FeedIndexRequestedEvent.upsert(feedId)))
          .doesNotThrowAnyException();
      verify(feedSearchRepository).save(any(FeedDocument.class));
    }

    @Test
    @DisplayName("인덱스 제거에 실패해도 예외를 던지지 않는다")
    void 인덱스_제거에_실패해도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      willThrow(new DataAccessResourceFailureException("ES 연결 실패"))
          .given(feedSearchRepository).deleteById(feedId.toString());

      // when & then
      assertThatCode(() -> listener.handle(FeedIndexRequestedEvent.delete(feedId)))
          .doesNotThrowAnyException();
      verify(feedSearchRepository).deleteById(feedId.toString());
      verify(feedSearchRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPSERT 대상이 소프트 삭제된 피드면 인덱스에서 제거한다")
    void UPSERT_대상이_소프트_삭제된_피드면_인덱스에서_제거한다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "삭제된 피드");
      feed.delete();
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      listener.handle(FeedIndexRequestedEvent.upsert(feedId));

      // then
      verify(feedSearchRepository).deleteById(feedId.toString());
      verify(feedSearchRepository, never()).save(any(FeedDocument.class));
    }

    @Test
    @DisplayName("문서 처리 오류가 나도 예외를 던지지 않는다")
    void 문서_처리_오류가_나도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));
      willThrow(new InvalidDataAccessApiUsageException("문서 매핑 오류"))
          .given(feedSearchRepository).save(any(FeedDocument.class));

      // when & then
      assertThatCode(() -> listener.handle(FeedIndexRequestedEvent.upsert(feedId)))
          .doesNotThrowAnyException();
      verify(feedSearchRepository).save(any(FeedDocument.class));
    }
  }

  @Nested
  @DisplayName("비동기 인덱싱 이벤트 처리")
  class HandleAsync {

    @Test
    @DisplayName("피드를 조회해 인덱스에 저장한다")
    void 피드를_조회해_인덱스에_저장한다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      listener.handleAsync(FeedIndexAsyncRequestedEvent.upsert(feedId));

      // then
      ArgumentCaptor<FeedDocument> captor = ArgumentCaptor.forClass(FeedDocument.class);
      verify(elasticsearchOperations).save(captor.capture());
      assertThat(captor.getValue().getId()).isEqualTo(feedId.toString());
    }

    @Test
    @DisplayName("강제 refresh를 유발하는 리포지토리를 쓰지 않는다")
    void 강제_refresh를_유발하는_리포지토리를_쓰지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      listener.handleAsync(FeedIndexAsyncRequestedEvent.upsert(feedId));

      // then
      verify(feedSearchRepository, never()).save(any(FeedDocument.class));
    }

    @Test
    @DisplayName("소프트 삭제된 피드면 인덱스에서 제거한다")
    void 소프트_삭제된_피드면_인덱스에서_제거한다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "삭제된 피드");
      feed.delete();
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      listener.handleAsync(FeedIndexAsyncRequestedEvent.upsert(feedId));

      // then
      verify(elasticsearchOperations).delete(feedId.toString(), FeedDocument.class);
      verify(elasticsearchOperations, never()).save(any(FeedDocument.class));
    }

    @Test
    @DisplayName("인덱스 저장에 실패해도 예외를 던지지 않는다")
    void 인덱스_저장에_실패해도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      Feed feed = feedWith(feedId, "오늘의 착장");
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));
      willThrow(new DataAccessResourceFailureException("ES 연결 실패"))
          .given(elasticsearchOperations).save(any(FeedDocument.class));

      // when & then
      assertThatCode(() -> listener.handleAsync(FeedIndexAsyncRequestedEvent.upsert(feedId)))
          .doesNotThrowAnyException();
      verify(elasticsearchOperations).save(any(FeedDocument.class));
    }
  }
}
