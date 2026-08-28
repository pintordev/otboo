package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedmigration.service.FeedIndexInspector;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.util.Set;
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
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexInitializer")
class FeedIndexInitializerTest {

  private static final IndexCoordinates INITIAL_INDEX = IndexCoordinates.of(
      FeedDocument.INDEX_NAME + "_v1");

  @InjectMocks
  FeedIndexInitializer initializer;

  @Mock
  ElasticsearchOperations elasticsearchOperations;

  @Mock
  FeedIndexInspector feedIndexInspector;

  @Mock
  IndexOperations entityOperations;

  @Mock
  IndexOperations targetOperations;

  private void givenAliasNotExists() {
    given(feedIndexInspector.exists()).willReturn(false);
    given(elasticsearchOperations.indexOps(eq(FeedDocument.class))).willReturn(entityOperations);
    given(elasticsearchOperations.indexOps(eq(INITIAL_INDEX))).willReturn(targetOperations);
  }

  @Nested
  @DisplayName("인덱스 초기화")
  class Run {

    @Test
    @DisplayName("alias가 없으면 첫 인덱스를 만들고 alias를 붙인다")
    void alias가_없으면_첫_인덱스를_만들고_alias를_붙인다() {
      // given
      givenAliasNotExists();
      given(feedIndexInspector.isAlias()).willReturn(false);
      Settings settings = new Settings();
      Document mapping = Document.create();
      given(entityOperations.createSettings()).willReturn(settings);
      given(entityOperations.createMapping()).willReturn(mapping);

      // when
      initializer.run(null);

      // then
      verify(targetOperations).create(settings, mapping);
      verify(targetOperations).alias(any(AliasActions.class));
    }

    @Test
    @DisplayName("alias가 이미 있으면 인덱스를 만들지 않는다")
    void alias가_이미_있으면_인덱스를_만들지_않는다() {
      // given
      given(feedIndexInspector.exists()).willReturn(true);
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());

      // when
      initializer.run(null);

      // then
      verify(targetOperations, never()).create(any(), any());
    }

    @Test
    @DisplayName("feeds가 alias가 아닌 실제 인덱스면 인덱스를 만들지 않는다")
    void feeds가_alias가_아닌_실제_인덱스면_인덱스를_만들지_않는다() {
      // given
      given(feedIndexInspector.exists()).willReturn(true);
      given(feedIndexInspector.isAlias()).willReturn(false);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
      verify(targetOperations, never()).create(any(), any());
    }

    @Test
    @DisplayName("다른 인스턴스가 먼저 생성했으면 예외를 삼킨다")
    void 다른_인스턴스가_먼저_생성했으면_예외를_삼킨다() {
      // given
      givenAliasNotExists();
      given(feedIndexInspector.isAlias()).willReturn(true);
      willThrow(new UncategorizedElasticsearchException(
          "resource_already_exists_exception: index [feeds_v1] already exists"))
          .given(targetOperations).create(any(), any());

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 존재 외의 오류는 전파한다")
    void 이미_존재_외의_오류는_전파한다() {
      // given
      givenAliasNotExists();
      willThrow(new DataAccessResourceFailureException("Connection refused"))
          .given(targetOperations).create(any(), any());

      // when & then
      assertThatThrownBy(() -> initializer.run(null))
          .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("기대하는 필드가 실제 매핑에 모두 있으면 경고하지 않는다")
    void 기대하는_필드가_실제_매핑에_모두_있으면_경고하지_않는다() {
      // given
      given(feedIndexInspector.exists()).willReturn(true);
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
      verify(targetOperations, never()).create(any(), any());
    }

    @Test
    @DisplayName("기대하는 필드가 실제 매핑에 없어도 기동을 막지 않는다")
    void 기대하는_필드가_실제_매핑에_없어도_기동을_막지_않는다() {
      // given
      given(feedIndexInspector.exists()).willReturn(true);
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of("searchText"));

      // when & then
      assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 인스턴스가 인덱스만 만들고 alias를 붙이지 않았으면 alias를 복구한다")
    void 다른_인스턴스가_인덱스만_만들고_alias를_붙이지_않았으면_alias를_복구한다() {
      // given
      givenAliasNotExists();
      given(feedIndexInspector.isAlias()).willReturn(false);
      willThrow(new UncategorizedElasticsearchException(
          "resource_already_exists_exception: index [feeds_v1] already exists"))
          .given(targetOperations).create(any(), any());

      // when
      initializer.run(null);

      // then
      verify(targetOperations).alias(any(AliasActions.class));
    }
  }
}
