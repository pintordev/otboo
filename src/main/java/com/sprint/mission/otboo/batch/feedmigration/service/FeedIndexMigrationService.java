package com.sprint.mission.otboo.batch.feedmigration.service;

import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexMigrationFailedException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

/**
 * 매핑 변경 시 새 인덱스로 무중단 전환한다.
 *
 * <p>재색인 소스는 DB다. {@code copy_to}가 색인 시점에만 동작해 {@code _reindex}로 인덱스를
 * 복사하면 {@code searchText}가 채워지지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedIndexMigrationService {

  private final JobOperator jobOperator;
  private final ElasticsearchOperations elasticsearchOperations;

  @Qualifier("feedIndexMigrationJob")
  private final Job feedIndexMigrationJob;

  public void migrate() {
    String currentIndex = currentIndexBehindAlias();
    String newIndex = FeedIndexNames.nextVersionOf(currentIndex);
    log.info("피드 인덱스 마이그레이션 시작: from={}, to={}", currentIndex, newIndex);

    createIndex(newIndex);
    reindexInto(newIndex);
    switchAlias(currentIndex, newIndex);
    deleteObsoleteIndex(newIndex);

    log.info("피드 인덱스 마이그레이션 완료: alias={}, index={}",
        FeedDocument.INDEX_NAME, newIndex);
  }

  private String currentIndexBehindAlias() {
    return aliasOps().getAliases(FeedDocument.INDEX_NAME).keySet().iterator().next();
  }

  private void createIndex(String newIndex) {
    IndexOperations entityOps = elasticsearchOperations.indexOps(FeedDocument.class);
    IndexOperations targetOps = indexOps(newIndex);

    if (targetOps.exists()) {
      targetOps.delete();
      log.warn("이전 실행에서 남은 인덱스를 삭제했습니다: index={}", newIndex);
    }

    targetOps.create(entityOps.createSettings(), entityOps.createMapping());
    log.info("새 피드 인덱스 생성 완료: index={}", newIndex);
  }

  private void reindexInto(String newIndex) {
    JobParameters parameters = new JobParametersBuilder()
        .addLong("time", Instant.now().toEpochMilli())
        .addString("targetIndex", newIndex)
        .toJobParameters();
    try {
      JobExecution execution = jobOperator.start(feedIndexMigrationJob, parameters);
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        throw FeedIndexMigrationFailedException.jobNotCompleted(execution.getStatus().name());
      }
    } catch (JobExecutionAlreadyRunningException | JobRestartException
             | JobInstanceAlreadyCompleteException | InvalidJobParametersException e) {
      throw FeedIndexMigrationFailedException.wrap(e);
    }
  }

  private IndexOperations aliasOps() {
    return indexOps(FeedDocument.INDEX_NAME);
  }

  private IndexOperations indexOps(String indexName) {
    return elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
  }

  // remove와 add를 한 요청에 담아야 alias가 어느 인덱스도 가리키지 않는 순간이 생기지 않는다.
  private void switchAlias(String currentIndex, String newIndex) {
    aliasOps().alias(new AliasActions(
        new AliasAction.Remove(aliasParameters(currentIndex)),
        new AliasAction.Add(aliasParameters(newIndex))));
    log.info("피드 인덱스 alias 전환 완료: alias={}, from={}, to={}",
        FeedDocument.INDEX_NAME, currentIndex, newIndex);
  }

  // 전환 직후 한 세대는 남겨, 문제가 생기면 alias만 되돌려 복구할 수 있게 한다.
  private void deleteObsoleteIndex(String newIndex) {
    FeedIndexNames.indexToDelete(newIndex).ifPresent(obsolete -> {
      IndexOperations obsoleteOps = indexOps(obsolete);
      if (obsoleteOps.exists()) {
        obsoleteOps.delete();
        log.info("오래된 피드 인덱스 삭제 완료: index={}", obsolete);
      }
    });
  }

  private AliasActionParameters aliasParameters(String indexName) {
    return AliasActionParameters.builder()
        .withIndices(indexName)
        .withAliases(FeedDocument.INDEX_NAME)
        .build();
  }
}
