package com.sprint.mission.otboo.batch.feedmigration.service;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 피드 검색 인덱스의 현재 상태를 조회한다.
 *
 * <p>기동 시 경고 로그({@code FeedIndexInitializer})와 관리자 화면의 상태 표시가 같은 판정을
 * 쓰도록 한곳에 모은다.
 */
@Component
@RequiredArgsConstructor
public class FeedIndexInspector {

  private static final String PROPERTIES = "properties";

  private final ElasticsearchOperations elasticsearchOperations;

  /**
   * {@code feeds}가 alias인지 실제 인덱스인지 판별한다.
   *
   * <p>{@code exists()}는 HEAD 요청이라 둘을 구분하지 못한다. {@code getAliases}는 alias가 아니면
   * {@code ResourceNotFoundException}을 던지므로, "alias가 아니다"라는 정상 상태를 boolean으로 바꾼다.
   */
  public boolean isAlias() {
    return currentIndexName().isPresent();
  }

  /**
   * alias가 가리키는 실제 인덱스 이름. alias가 아니면 비어 있다.
   */
  public Optional<String> currentIndexName() {
    try {
      return aliasOps().getAliases(FeedDocument.INDEX_NAME).keySet().stream().findFirst();
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    }
  }

  /**
   * FeedDocument가 기대하지만 실제 매핑에 없는 필드.
   *
   * <p>필드 이름만 비교한다. 타입·analyzer는 ES가 정규화해 돌려주므로 그대로 비교하면 오탐이 난다.
   */
  public Set<String> missingFields() {
    Set<String> expected = fieldNames(
        elasticsearchOperations.indexOps(FeedDocument.class).createMapping());
    Set<String> actual = fieldNames(aliasOps().getMapping());

    Set<String> missing = new LinkedHashSet<>(expected);
    missing.removeAll(actual);
    return missing;
  }

  public boolean exists() {
    return aliasOps().exists();
  }

  private IndexOperations aliasOps() {
    return elasticsearchOperations.indexOps(IndexCoordinates.of(FeedDocument.INDEX_NAME));
  }

  // getMapping()은 인덱스 이름과 mappings로 감싸져 올 수 있어 properties를 찾아 내려간다.
  private Set<String> fieldNames(Map<String, Object> mapping) {
    Map<String, Object> properties = findProperties(mapping);
    return properties == null ? Set.of() : new LinkedHashSet<>(properties.keySet());
  }

  /**
   * 인덱스에 색인된 문서 수.
   */
  public long indexedCount() {
    return elasticsearchOperations.count(Query.findAll(), FeedDocument.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findProperties(Map<String, Object> node) {
    Object properties = node.get(PROPERTIES);
    if (properties instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    for (Object value : node.values()) {
      if (value instanceof Map<?, ?> child) {
        Map<String, Object> found = findProperties((Map<String, Object>) child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
