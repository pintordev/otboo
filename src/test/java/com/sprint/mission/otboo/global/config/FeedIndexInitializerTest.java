package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexInitializer")
class FeedIndexInitializerTest {

  @InjectMocks
  FeedIndexInitializer initializer;

  @Mock
  ElasticsearchOperations elasticsearchOperations;

  @Mock
  IndexOperations indexOperations;

  @Nested
  @DisplayName("인덱스 초기화")
  class Run {

    @Test
    @DisplayName("인덱스가 없으면 매핑과 함께 생성한다")
    void 인덱스가_없으면_매핑과_함께_생성한다() {
      // given
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(indexOperations);
      given(indexOperations.exists()).willReturn(false);

      // when
      initializer.run(null);

      // then
      verify(indexOperations).createWithMapping();
    }

    @Test
    @DisplayName("인덱스가 이미 있으면 생성하지 않는다")
    void 인덱스가_이미_있으면_생성하지_않는다() {
      // given
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(indexOperations);
      given(indexOperations.exists()).willReturn(true);

      // when
      initializer.run(null);

      // then
      verify(indexOperations, never()).createWithMapping();
    }

    @Test
    @DisplayName("다른 인스턴스가 먼저 생성했으면 예외를 삼킨다")
    void 다른_인스턴스가_먼저_생성했으면_예외를_삼킨다() {
      // given
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(indexOperations);
      given(indexOperations.exists()).willReturn(false);
      willThrow(new UncategorizedElasticsearchException(
          "resource_already_exists_exception: index [feeds] already exists"))
          .given(indexOperations).createWithMapping();

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 존재 외의 오류는 전파한다")
    void 이미_존재_외의_오류는_전파한다() {
      // given
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(indexOperations);
      given(indexOperations.exists()).willReturn(false);
      willThrow(new DataAccessResourceFailureException("Connection refused"))
          .given(indexOperations).createWithMapping();

      // when & then
      assertThatThrownBy(() -> initializer.run(null))
          .isInstanceOf(DataAccessResourceFailureException.class);
    }
  }
}
