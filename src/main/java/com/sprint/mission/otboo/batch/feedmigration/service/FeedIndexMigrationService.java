package com.sprint.mission.otboo.batch.feedmigration.service;

import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexMigrationResult;
import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexStatus;
import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexMigrationFailedException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매핑 변경 시 새 인덱스로 무중단 전환한다.
 *
 * <p>재색인 소스는 DB다. {@code copy_to}가 색인 시점에만 동작해 {@code _reindex}로 인덱스를
 * 복사하면 {@code searchText}가 채워지지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedIndexMigrationService {

  private final JobOperator jobOperator;
  private final ElasticsearchOperations elasticsearchOperations;
  private final FeedIndexInspector feedIndexInspector;
  private final FeedRepository feedRepository;
  private final JobRepository jobRepository;

  @Qualifier("feedIndexMigrationJob")
  private final Job feedIndexMigrationJob;

  @SchedulerLock(name = "FeedIndexMigrationLock", lockAtMostFor = "PT2H")
  public FeedIndexMigrationResult migrate() {
    long startedAt = System.currentTimeMillis();

    String currentIndex = currentIndexBehindAlias();
    String newIndex = FeedIndexNames.nextVersionOf(currentIndex);
    log.info("피드 인덱스 마이그레이션 시작: from={}, to={}", currentIndex, newIndex);

    createIndex(newIndex);
    long indexedCount = reindexInto(newIndex);
    switchAlias(currentIndex, newIndex);
    refreshIndex(newIndex);
    deleteObsoleteIndex(newIndex);

    log.info("피드 인덱스 마이그레이션 완료: alias={}, index={}",
        FeedDocument.INDEX_NAME, newIndex);

    return new FeedIndexMigrationResult(
        currentIndex, newIndex, indexedCount, System.currentTimeMillis() - startedAt);
  }

  public FeedIndexStatus readStatus() {
    return new FeedIndexStatus(
        FeedDocument.INDEX_NAME,
        feedIndexInspector.currentIndexName().orElse(null),
        feedIndexInspector.isAlias(),
        feedIndexInspector.missingFields(),
        feedIndexInspector.indexedCount(),
        feedRepository.countActive(),
        lastCompletedAt("feedReindexJob"),
        lastCompletedAt("feedIndexMigrationJob"));
  }

  /**
   * 마지막으로 완료된 Job 실행 시각.
   *
   * <p>Job 인스턴스는 실행 시각(time 파라미터)마다 새로 만들어진다. 가장 최근 인스턴스가
   * 실행 중이거나 실패했을 수 있어, 완료된 실행을 찾을 때까지 최대 5페이지(50개)를 훑는다.
   */
  private Instant lastCompletedAt(String jobName) {
    int pageSize = 10;
    int maxPages = 5;

    for (int page = 0; page < maxPages; page++) {
      List<JobInstance> instances = jobRepository.getJobInstances(jobName, page * pageSize,
          pageSize);
      if (instances.isEmpty()) {
        return null;
      }
      for (JobInstance instance : instances) {
        Optional<Instant> completedAt = latestCompletedEndTime(instance);
        if (completedAt.isPresent()) {
          return completedAt.get();
        }
      }
    }
    return null;
  }

  private Optional<Instant> latestCompletedEndTime(JobInstance instance) {
    return jobRepository.getJobExecutions(instance).stream()
        .filter(execution -> execution.getStatus() == BatchStatus.COMPLETED)
        .map(JobExecution::getEndTime)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .map(endTime -> endTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  private String currentIndexBehindAlias() {
    return aliasOps().getAliases(FeedDocument.INDEX_NAME).keySet().iterator().next();
  }

  private void createIndex(String newIndex) {
    IndexOperations entityOps = elasticsearchOperations.indexOps(FeedDocument.class);
    IndexOperations targetOps = indexOps(newIndex);

    if (targetOps.exists() && !targetOps.delete()) {
      log.error("이전 실행에서 남은 인덱스 삭제 실패: index={}", newIndex);
      throw FeedIndexMigrationFailedException.operationRejected("delete", newIndex);
    }

    if (!targetOps.create(entityOps.createSettings(), entityOps.createMapping())) {
      log.error("새 인덱스 생성 거부: index={}", newIndex);
      throw FeedIndexMigrationFailedException.operationRejected("create", newIndex);
    }
    log.info("새 피드 인덱스 생성 완료: index={}", newIndex);
  }

  private long reindexInto(String newIndex) {
    JobParameters parameters = new JobParametersBuilder()
        .addLong("time", Instant.now().toEpochMilli())
        .addString("targetIndex", newIndex)
        .toJobParameters();
    try {
      JobExecution execution = jobOperator.start(feedIndexMigrationJob, parameters);
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        log.error("마이그레이션 재색인 정상 종료 실패: index={}, status={}",
            newIndex, execution.getStatus());
        throw FeedIndexMigrationFailedException.jobNotCompleted(execution.getStatus().name());
      }
      return execution.getStepExecutions().stream()
          .mapToLong(StepExecution::getWriteCount)
          .sum();
    } catch (JobExecutionAlreadyRunningException | JobRestartException
             | JobInstanceAlreadyCompleteException | InvalidJobParametersException e) {
      log.error("마이그레이션 재색인 실행 실패: index={}", newIndex, e);
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
    boolean switched = aliasOps().alias(new AliasActions(
        new AliasAction.Remove(aliasParameters(currentIndex)),
        new AliasAction.Add(aliasParameters(newIndex))));

    if (!switched) {
      log.error("alias 전환 거부: alias={}, from={}, to={}",
          FeedDocument.INDEX_NAME, currentIndex, newIndex);
      throw FeedIndexMigrationFailedException.operationRejected("alias", newIndex);
    }
    log.info("피드 인덱스 alias 전환 완료: alias={}, from={}, to={}",
        FeedDocument.INDEX_NAME, currentIndex, newIndex);
  }

  // 재색인은 bulk API로 refresh 없이 색인한다. 전환 직후 검색과 문서 수 조회가
  // 바로 맞도록 한 번 refresh한다. 마이그레이션은 수동 트리거라 비용이 문제되지 않는다.
  private void refreshIndex(String newIndex) {
    indexOps(newIndex).refresh();
    log.info("새 피드 인덱스 refresh 완료: index={}", newIndex);
  }

  // 전환 직후 한 세대는 남겨, 문제가 생기면 alias만 되돌려 복구할 수 있게 한다.
  private void deleteObsoleteIndex(String newIndex) {
    FeedIndexNames.indexToDelete(newIndex).ifPresent(obsolete -> {
      IndexOperations obsoleteOps = indexOps(obsolete);
      if (!obsoleteOps.exists()) {
        return;
      }
      if (!obsoleteOps.delete()) {
        log.error("오래된 인덱스 삭제 거부: index={}", obsolete);
        throw FeedIndexMigrationFailedException.operationRejected("delete", obsolete);
      }
      log.info("오래된 피드 인덱스 삭제 완료: index={}", obsolete);
    });
  }

  private AliasActionParameters aliasParameters(String indexName) {
    return AliasActionParameters.builder()
        .withIndices(indexName)
        .withAliases(FeedDocument.INDEX_NAME)
        .build();
  }
}
