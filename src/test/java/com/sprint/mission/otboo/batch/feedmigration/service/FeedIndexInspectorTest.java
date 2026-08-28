package com.sprint.mission.otboo.batch.feedmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexInspector")
class FeedIndexInspectorTest {

  private static final IndexCoordinates ALIAS = IndexCoordinates.of(FeedDocument.INDEX_NAME);

  @InjectMocks
  FeedIndexInspector inspector;

  @Mock
  ElasticsearchOperations elasticsearchOperations;

  @Mock
  IndexOperations aliasOperations;

  @Mock
  IndexOperations entityOperations;

  private Document mappingWith(String... fieldNames) {
    Map<String, Object> properties = new LinkedHashMap<>();
    for (String name : fieldNames) {
      properties.put(name, Map.of("type", "keyword"));
    }
    Document mapping = Document.create();
    mapping.put("properties", properties);
    return mapping;
  }

  @Nested
  @DisplayName("alias 판별")
  class IsAlias {

    @Test
    @DisplayName("alias면 가리키는 인덱스 이름을 반환한다")
    void alias면_가리키는_인덱스_이름을_반환한다() {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of("feeds_v1", Set.of()));

      // when & then
      assertThat(inspector.isAlias()).isTrue();
      assertThat(inspector.currentIndexName()).hasValue("feeds_v1");
    }

    @Test
    @DisplayName("실제 인덱스면 비어 있다")
    void 실제_인덱스면_비어_있다() {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      willThrow(new ResourceNotFoundException("alias [feeds] missing"))
          .given(aliasOperations).getAliases(FeedDocument.INDEX_NAME);

      // when & then
      assertThat(inspector.isAlias()).isFalse();
      assertThat(inspector.currentIndexName()).isEmpty();
    }
  }

  @Nested
  @DisplayName("매핑 누락 필드 조회")
  class MissingFields {

    @Test
    @DisplayName("기대하는 필드가 실제 매핑에 없으면 그 필드를 반환한다")
    void 기대하는_필드가_실제_매핑에_없으면_그_필드를_반환한다() {
      // given
      given(elasticsearchOperations.indexOps(eq(FeedDocument.class))).willReturn(entityOperations);
      given(entityOperations.createMapping()).willReturn(mappingWith("id", "searchText"));
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getMapping()).willReturn(mappingWith("id"));

      // when & then
      assertThat(inspector.missingFields()).containsExactly("searchText");
    }

    @Test
    @DisplayName("실제 매핑에 필드가 더 있어도 누락으로 보지 않는다")
    void 실제_매핑에_필드가_더_있어도_누락으로_보지_않는다() {
      // given
      given(elasticsearchOperations.indexOps(eq(FeedDocument.class))).willReturn(entityOperations);
      given(entityOperations.createMapping()).willReturn(mappingWith("id"));
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getMapping()).willReturn(mappingWith("id", "took", "hits"));

      // when & then
      assertThat(inspector.missingFields()).isEmpty();
    }

    @Test
    @DisplayName("인덱스 이름으로 감싸진 매핑에서도 필드를 찾는다")
    void 인덱스_이름으로_감싸진_매핑에서도_필드를_찾는다() {
      // given
      Document wrapped = Document.create();
      wrapped.put("feeds_v1", Map.of("mappings", mappingWith("id")));

      given(elasticsearchOperations.indexOps(eq(FeedDocument.class))).willReturn(entityOperations);
      given(entityOperations.createMapping()).willReturn(mappingWith("id"));
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getMapping()).willReturn(wrapped);

      // when & then
      assertThat(inspector.missingFields()).isEmpty();
    }
  }
}
