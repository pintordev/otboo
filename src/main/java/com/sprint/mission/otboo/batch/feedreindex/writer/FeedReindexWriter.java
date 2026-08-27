package com.sprint.mission.otboo.batch.feedreindex.writer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexBulkException;
import com.sprint.mission.otboo.batch.feedreindex.metrics.FeedReindexMetrics;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@RequiredArgsConstructor
@Component
public class FeedReindexWriter implements ItemWriter<Feed> {

  private static final int STATUS_CONFLICT = 409;
  private static final String INDEX_FAILURE_MARKER = "FEED_REINDEX_FAILED";

  private final ElasticsearchClient elasticsearchClient;
  private final FeedSearchRepository feedSearchRepository;
  private final EntityManager entityManager;
  private final FeedReindexMetrics feedReindexMetrics;

  @Value("#{stepExecution.stepName}")
  private final String stepName;

  @Value("#{jobParameters['targetIndex']}")
  private final String targetIndex;

  // Reader가 읽은 뒤 Writer가 쓰기 전에 사용자가 수정하면 오래된 문서가 최신을 덮을 수 있다.
  // FeedDocument에 updatedAt 기반 외부 버전(EXTERNAL_GTE)을 실어 ES가 거부하게 했고,
  // 거부된 건은 FeedReindexSkipPolicy가 정상 동작으로 보고 건너뛴다.
  @Override
  public void write(Chunk<? extends Feed> chunk) throws IOException {
    if (chunk.isEmpty()) {
      return;
    }

    List<FeedDocument> documents = chunk.getItems().stream()
        .map(FeedDocument::from)
        .toList();

    long drift = countDrift(documents);
    long conflicts = index(documents);
    feedReindexMetrics.countDrift(stepName, drift);

    entityManager.clear();
    log.info("피드 재색인 chunk 완료: size={}, drift={}, conflicts={}",
        documents.size(), drift, conflicts);
  }

  // Spring Data의 saveAll은 부분 실패를 BulkFailureException으로 감싸 던져 409(버전 충돌)를 정상 결과로 분리할 수 없다.
  // bulk API를 직접 써서 항목별 응답을 본다.
  //
  // 버전 충돌은 Reader가 읽은 뒤 사용자가 그 피드를 수정해 더 최신 문서가 색인됐다는 뜻이므로 건너뛰고,
  // 그 외 실패만 예외로 올려 Step의 skip 정책이 판단하게 한다.
  private long index(List<FeedDocument> documents) throws IOException {
    BulkRequest request = BulkRequest.of(b -> b
        .operations(documents.stream()
            .map(doc -> BulkOperation.of(op -> op
                .index(idx -> idx
                    .index(targetIndex)
                    .id(doc.getId())
                    .version(doc.getVersion())
                    .versionType(VersionType.ExternalGte)
                    .document(doc))))
            .toList()));

    BulkResponse response = elasticsearchClient.bulk(request);
    if (!response.errors()) {
      return 0;
    }

    List<BulkResponseItem> failures = response.items().stream()
        .filter(item -> item.error() != null)
        .toList();
    List<BulkResponseItem> unexpected = failures.stream()
        .filter(item -> item.status() != STATUS_CONFLICT)
        .toList();

    if (!unexpected.isEmpty()) {
      unexpected.forEach(item -> log.error("{} feedId={}, status={}, error={}",
          INDEX_FAILURE_MARKER, item.id(), item.status(), item.error().type()));
      throw FeedReindexBulkException.of(unexpected.size());
    }

    failures.forEach(item ->
        log.debug("피드 재색인 버전 충돌로 건너뜀: feedId={}", item.id()));
    return failures.size();
  }

  // 이 배치는 전 건을 다시 쓰므로 writeCount가 항상 활성 피드 수와 같다.
  // 어긋난 문서가 0건이든 500건이든 처리량 지표는 동일하므로, 실제 교정한 건수를 따로 센다.
  // 청크당 mget 한 번이 추가되지만 500건 한 요청이라 부담은 작다.
  private long countDrift(List<FeedDocument> documents) {
    List<String> ids = documents.stream().map(FeedDocument::getId).toList();
    Map<String, FeedDocument> indexed = StreamSupport
        .stream(feedSearchRepository.findAllById(ids).spliterator(), false)
        .collect(Collectors.toMap(FeedDocument::getId, Function.identity()));

    return documents.stream()
        .filter(doc -> !doc.isConsistentWith(indexed.get(doc.getId())))
        .count();
  }
}
