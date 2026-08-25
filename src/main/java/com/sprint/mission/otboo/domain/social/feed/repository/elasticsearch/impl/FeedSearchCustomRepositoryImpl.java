package com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSearchResult;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchCustomRepository;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class FeedSearchCustomRepositoryImpl implements FeedSearchCustomRepository {

  private static final String FIELD_SEARCH_TEXT = "searchText";
  private static final String FIELD_SKY_STATUS = "skyStatus";
  private static final String FIELD_PRECIPITATION_TYPE = "precipitationType";
  private static final String FIELD_AUTHOR_ID = "authorId";
  private static final String FIELD_CREATED_AT = "createdAt";
  private static final String FIELD_LIKE_COUNT = "likeCount";
  private static final String FIELD_ID = "id";

  private final ElasticsearchOperations operations;

  @Override
  public FeedSearchResult search(FeedListParams params) {
    SearchHits<FeedDocument> hits =
        operations.search(buildNativeQuery(params), FeedDocument.class);
    return toSearchResult(hits, params);
  }

  private FeedSearchResult toSearchResult(SearchHits<FeedDocument> hits, FeedListParams params) {
    List<SearchHit<FeedDocument>> raw = hits.getSearchHits();
    boolean hasNext = raw.size() > params.limit();
    List<SearchHit<FeedDocument>> page = hasNext ? raw.subList(0, params.limit()) : raw;

    List<UUID> feedIds = page.stream()
        .map(SearchHit::getId)
        .map(UUID::fromString)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !page.isEmpty()) {
      SearchHit<FeedDocument> last = page.get(page.size() - 1);
      nextCursor = extractCursor(last, params.sortBy());
      nextIdAfter = UUID.fromString(String.valueOf(last.getSortValues().get(1)));
    }

    return new FeedSearchResult(feedIds, hits.getTotalHits(), nextCursor, nextIdAfter, hasNext);
  }

  private String extractCursor(SearchHit<FeedDocument> hit, FeedSortBy sortBy) {
    Object sortValue = hit.getSortValues().get(0);
    return switch (sortBy) {
      case CREATED_AT -> Instant.ofEpochMilli(Long.parseLong(String.valueOf(sortValue))).toString();
      case LIKE_COUNT -> String.valueOf(sortValue);
    };
  }

  private NativeQuery buildNativeQuery(FeedListParams params) {
    NativeQueryBuilder builder = NativeQuery.builder()
        .withQuery(buildQuery(params))
        .withSort(buildSort(params))
        .withMaxResults(params.limit() + 1)
        .withTrackTotalHits(true);

    if (params.cursor() != null) {
      builder.withSearchAfter(buildSearchAfter(params));
    }
    return builder.build();
  }

  // 커서 → ES 정렬 값. createdAt은 epoch millis로 인덱싱되므로 Instant를 변환한다.
  private List<Object> buildSearchAfter(FeedListParams params) {
    Object sortValue = switch (params.sortBy()) {
      case CREATED_AT -> Instant.parse(params.cursor()).toEpochMilli();
      case LIKE_COUNT -> Long.parseLong(params.cursor());
    };
    return List.of(sortValue, params.idAfter().toString());
  }

  private Query buildQuery(FeedListParams params) {
    return Query.of(q -> q.bool(b -> b
        .must(buildKeywordQuery(params.keywordLike()))
        .filter(buildFilters(params))));
  }

  private Query buildKeywordQuery(String keywordLike) {
    if (!StringUtils.hasText(keywordLike)) {
      return Query.of(q -> q.matchAll(m -> m));
    }
    // 토큰 2개 이하면 전부 요구하고, 3개 이상이면 75%만 요구한다.
    return Query.of(q -> q.match(m -> m
        .field(FIELD_SEARCH_TEXT)
        .query(keywordLike)
        .minimumShouldMatch("2<75%")));
  }

  private List<Query> buildFilters(FeedListParams params) {
    List<Query> filters = new ArrayList<>();
    addTermFilter(filters, FIELD_SKY_STATUS, params.skyStatusEqual());
    addTermFilter(filters, FIELD_PRECIPITATION_TYPE, params.precipitationTypeEqual());
    addTermFilter(filters, FIELD_AUTHOR_ID, params.authorIdEqual());
    return filters;
  }

  // 정렬 필드 + _id tie-break. QueryDSL의 sortOrderSpecifier + idOrderSpecifier에 대응한다.
  private List<SortOptions> buildSort(FeedListParams params) {
    SortOrder order = toSortOrder(params.sortDirection());
    return List.of(
        fieldSort(sortField(params.sortBy()), order),
        fieldSort(FIELD_ID, order)
    );
  }

  private SortOrder toSortOrder(SortDirection direction) {
    return direction == SortDirection.ASCENDING ? SortOrder.Asc : SortOrder.Desc;
  }

  private String sortField(FeedSortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> FIELD_CREATED_AT;
      case LIKE_COUNT -> FIELD_LIKE_COUNT;
    };
  }

  private SortOptions fieldSort(String field, SortOrder order) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
  }

  // enum은 name()으로 인덱싱되므로 상수명으로 비교한다.
  private void addTermFilter(List<Query> filters, String field, Enum<?> value) {
    if (value != null) {
      filters.add(termFilter(field, value.name()));
    }
  }

  // UUID는 문자열로 인덱싱되므로 toString()으로 비교한다.
  private void addTermFilter(List<Query> filters, String field, UUID value) {
    if (value != null) {
      filters.add(termFilter(field, value.toString()));
    }
  }

  private Query termFilter(String field, String value) {
    return Query.of(q -> q.term(t -> t.field(field).value(value)));
  }
}
