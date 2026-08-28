package com.sprint.mission.otboo.batch.feedmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexStatus;
import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexMigrationFailedException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexMigrationService")
class FeedIndexMigrationServiceTest {

  private static final IndexCoordinates ALIAS = IndexCoordinates.of(FeedDocument.INDEX_NAME);
  private static final IndexCoordinates CURRENT_INDEX = IndexCoordinates.of("feeds_v1");
  private static final IndexCoordinates NEW_INDEX = IndexCoordinates.of("feeds_v2");

  @Mock
  private JobOperator jobOperator;

  @Mock
  private ElasticsearchOperations elasticsearchOperations;

  @Mock
  private Job feedIndexMigrationJob;

  @Mock
  private JobExecution jobExecution;

  @Mock
  private IndexOperations aliasOperations;

  @Mock
  private IndexOperations entityOperations;

  @Mock
  private IndexOperations newIndexOperations;

  @Mock
  private IndexOperations obsoleteIndexOperations;

  private FeedIndexMigrationService feedIndexMigrationService;

  @Mock
  private FeedIndexInspector feedIndexInspector;

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private JobRepository jobRepository;

  @BeforeEach
  void setUp() {
    feedIndexMigrationService = new FeedIndexMigrationService(
        jobOperator, elasticsearchOperations, feedIndexInspector,
        feedRepository, jobRepository, feedIndexMigrationJob);
  }

  private void givenAliasPointsToCurrentIndex() {
    given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
    given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
        .willReturn(Map.of(CURRENT_INDEX.getIndexName(), Set.of()));
    given(aliasOperations.alias(any(AliasActions.class))).willReturn(true);
  }

  private void givenNewIndexCreated() {
    given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
    given(entityOperations.createSettings()).willReturn(new Settings());
    given(entityOperations.createMapping()).willReturn(Document.create());
    given(elasticsearchOperations.indexOps(eq(NEW_INDEX))).willReturn(newIndexOperations);
    given(newIndexOperations.create(any(Settings.class), any(Document.class))).willReturn(true);
  }

  private void givenJobCompleted() throws Exception {
    given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
    given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
  }

  @Nested
  @DisplayName("인덱스 마이그레이션")
  class Migrate {

    @Test
    @DisplayName("alias가 가리키는 인덱스의 다음 버전으로 새 인덱스를 만든다")
    void alias가_가리키는_인덱스의_다음_버전으로_새_인덱스를_만든다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      givenJobCompleted();

      // when
      feedIndexMigrationService.migrate();

      // then
      verify(elasticsearchOperations, atLeastOnce()).indexOps(eq(NEW_INDEX));
      verify(newIndexOperations).create(any(Settings.class), any(Document.class));
    }

    @Test
    @DisplayName("새 인덱스를 대상으로 재색인 Job을 실행한다")
    void 새_인덱스를_대상으로_재색인_Job을_실행한다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      givenJobCompleted();

      // when
      feedIndexMigrationService.migrate();

      // then
      ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
      verify(jobOperator).start(eq(feedIndexMigrationJob), captor.capture());
      assertThat(captor.getValue().getString("targetIndex"))
          .isEqualTo(NEW_INDEX.getIndexName());
    }

    @Test
    @DisplayName("재색인이 끝나면 alias를 새 인덱스로 전환한다")
    void 재색인이_끝나면_alias를_새_인덱스로_전환한다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      givenJobCompleted();

      // when
      feedIndexMigrationService.migrate();

      // then
      InOrder inOrder = inOrder(jobOperator, aliasOperations);
      inOrder.verify(jobOperator).start(eq(feedIndexMigrationJob), any(JobParameters.class));
      inOrder.verify(aliasOperations).alias(any(AliasActions.class));
    }

    @Test
    @DisplayName("alias 전환 후 새 인덱스를 refresh한다")
    void alias_전환_후_새_인덱스를_refresh한다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      givenJobCompleted();

      // when
      feedIndexMigrationService.migrate();

      // then
      InOrder inOrder = inOrder(aliasOperations, newIndexOperations);
      inOrder.verify(aliasOperations).alias(any(AliasActions.class));
      inOrder.verify(newIndexOperations).refresh();
    }

    @Test
    @DisplayName("Job이 정상 종료되지 않으면 alias를 전환하지 않는다")
    void Job이_정상_종료되지_않으면_alias를_전환하지_않는다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of(CURRENT_INDEX.getIndexName(), Set.of()));
      givenNewIndexCreated();
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);

      // when & then
      assertThatThrownBy(() -> feedIndexMigrationService.migrate())
          .isInstanceOf(FeedIndexMigrationFailedException.class);
      verify(aliasOperations, never()).alias(any(AliasActions.class));
    }

    @Test
    @DisplayName("alias 전환이 거부되면 오래된 인덱스를 삭제하지 않는다")
    void alias_전환이_거부되면_오래된_인덱스를_삭제하지_않는다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of(CURRENT_INDEX.getIndexName(), Set.of()));
      given(aliasOperations.alias(any(AliasActions.class))).willReturn(false);
      givenNewIndexCreated();
      givenJobCompleted();

      // when & then
      assertThatThrownBy(() -> feedIndexMigrationService.migrate())
          .isInstanceOf(FeedIndexMigrationFailedException.class);
      verify(obsoleteIndexOperations, never()).delete();
    }

    @Test
    @DisplayName("새 인덱스 생성이 거부되면 재색인하지 않는다")
    void 새_인덱스_생성이_거부되면_재색인하지_않는다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of(CURRENT_INDEX.getIndexName(), Set.of()));
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
      given(entityOperations.createSettings()).willReturn(new Settings());
      given(entityOperations.createMapping()).willReturn(Document.create());
      given(elasticsearchOperations.indexOps(eq(NEW_INDEX))).willReturn(newIndexOperations);
      given(newIndexOperations.create(any(Settings.class), any(Document.class))).willReturn(false);

      // when & then
      assertThatThrownBy(() -> feedIndexMigrationService.migrate())
          .isInstanceOf(FeedIndexMigrationFailedException.class);
      verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("한 세대를 남기고 오래된 인덱스를 삭제한다")
    void 한_세대를_남기고_오래된_인덱스를_삭제한다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of("feeds_v3", Set.of()));
      given(aliasOperations.alias(any(AliasActions.class))).willReturn(true);
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
      given(entityOperations.createSettings()).willReturn(new Settings());
      given(entityOperations.createMapping()).willReturn(Document.create());
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v4"))))
          .willReturn(newIndexOperations);
      given(newIndexOperations.create(any(Settings.class), any(Document.class))).willReturn(true);
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v2"))))
          .willReturn(obsoleteIndexOperations);
      given(obsoleteIndexOperations.exists()).willReturn(true);
      given(obsoleteIndexOperations.exists()).willReturn(true);
      given(obsoleteIndexOperations.delete()).willReturn(true);
      givenJobCompleted();

      // when
      feedIndexMigrationService.migrate();

      // then
      verify(obsoleteIndexOperations).delete();
    }

    @Test
    @DisplayName("이전 실행에서 남은 인덱스가 있으면 삭제하고 새로 만든다")
    void 이전_실행에서_남은_인덱스가_있으면_삭제하고_새로_만든다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      givenJobCompleted();
      given(newIndexOperations.exists()).willReturn(true);
      given(newIndexOperations.delete()).willReturn(true);

      // when
      feedIndexMigrationService.migrate();

      // then
      InOrder inOrder = inOrder(newIndexOperations);
      inOrder.verify(newIndexOperations).delete();
      inOrder.verify(newIndexOperations).create(any(Settings.class), any(Document.class));
    }

    @Test
    @DisplayName("오래된 인덱스 삭제가 거부되면 예외를 던진다")
    void 오래된_인덱스_삭제가_거부되면_예외를_던진다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of("feeds_v3", Set.of()));
      given(aliasOperations.alias(any(AliasActions.class))).willReturn(true);
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
      given(entityOperations.createSettings()).willReturn(new Settings());
      given(entityOperations.createMapping()).willReturn(Document.create());
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v4"))))
          .willReturn(newIndexOperations);
      given(newIndexOperations.create(any(Settings.class), any(Document.class))).willReturn(true);
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v2"))))
          .willReturn(obsoleteIndexOperations);
      given(obsoleteIndexOperations.exists()).willReturn(true);
      given(obsoleteIndexOperations.delete()).willReturn(false);
      givenJobCompleted();

      // when & then
      assertThatThrownBy(() -> feedIndexMigrationService.migrate())
          .isInstanceOf(FeedIndexMigrationFailedException.class);
    }
  }

  @Nested
  @DisplayName("인덱스 상태 조회")
  class ReadStatus {

    @Test
    @DisplayName("alias 여부와 누락 필드, 문서 수를 조합해 반환한다")
    void alias_여부와_누락_필드_문서_수를_조합해_반환한다() {
      // given
      given(feedIndexInspector.currentIndexName()).willReturn(Optional.of("feeds_v1"));
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of("searchText"));
      given(feedIndexInspector.indexedCount()).willReturn(26L);
      given(feedRepository.countActive()).willReturn(30L);

      // when
      FeedIndexStatus status = feedIndexMigrationService.readStatus();

      // then
      assertThat(status).isEqualTo(new FeedIndexStatus(
          "feeds", "feeds_v1", true, Set.of("searchText"), 26L, 30L, null, null));
    }

    @Test
    @DisplayName("alias가 아니면 현재 인덱스가 비어 있다")
    void alias가_아니면_현재_인덱스가_비어_있다() {
      // given
      given(feedIndexInspector.currentIndexName()).willReturn(Optional.empty());
      given(feedIndexInspector.isAlias()).willReturn(false);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());
      given(feedIndexInspector.indexedCount()).willReturn(0L);
      given(feedRepository.countActive()).willReturn(30L);

      // when
      FeedIndexStatus status = feedIndexMigrationService.readStatus();

      // then
      assertThat(status.currentIndex()).isNull();
      assertThat(status.alias()).isFalse();
    }

    @Test
    @DisplayName("마지막으로 완료된 Job 실행 시각을 반환한다")
    void 마지막으로_완료된_Job_실행_시각을_반환한다() {
      // given
      given(feedIndexInspector.currentIndexName()).willReturn(Optional.of("feeds_v1"));
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());
      given(feedIndexInspector.indexedCount()).willReturn(26L);
      given(feedRepository.countActive()).willReturn(26L);

      JobInstance instance = new JobInstance(1L, "feedReindexJob");
      LocalDateTime endTime = LocalDateTime.of(2026, 8, 28, 5, 0);
      given(jobRepository.getJobInstances("feedReindexJob", 0, 10))
          .willReturn(List.of(instance));
      given(jobRepository.getJobExecutions(instance)).willReturn(List.of(jobExecution));
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(jobExecution.getEndTime()).willReturn(endTime);
      given(jobRepository.getJobInstances("feedIndexMigrationJob", 0, 10))
          .willReturn(List.of());

      // when
      FeedIndexStatus status = feedIndexMigrationService.readStatus();

      // then
      assertThat(status.lastReindexAt())
          .isEqualTo(endTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("최근 인스턴스가 실패했으면 이전 완료 인스턴스를 찾는다")
    void 최근_인스턴스가_실패했으면_이전_완료_인스턴스를_찾는다() {
      // given
      given(feedIndexInspector.currentIndexName()).willReturn(Optional.of("feeds_v1"));
      given(feedIndexInspector.isAlias()).willReturn(true);
      given(feedIndexInspector.missingFields()).willReturn(Set.of());
      given(feedIndexInspector.indexedCount()).willReturn(26L);
      given(feedRepository.countActive()).willReturn(26L);

      JobInstance failedInstance = new JobInstance(2L, "feedReindexJob");
      JobInstance completedInstance = new JobInstance(1L, "feedReindexJob");
      JobExecution failedExecution = mock(JobExecution.class);
      JobExecution completedExecution = mock(JobExecution.class);
      LocalDateTime endTime = LocalDateTime.of(2026, 8, 28, 5, 0);

      given(jobRepository.getJobInstances("feedReindexJob", 0, 10))
          .willReturn(List.of(failedInstance, completedInstance));
      given(jobRepository.getJobExecutions(failedInstance)).willReturn(List.of(failedExecution));
      given(failedExecution.getStatus()).willReturn(BatchStatus.FAILED);
      given(jobRepository.getJobExecutions(completedInstance)).willReturn(
          List.of(completedExecution));
      given(completedExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(completedExecution.getEndTime()).willReturn(endTime);

      given(jobRepository.getJobInstances("feedIndexMigrationJob", 0, 10))
          .willReturn(List.of());

      // when
      FeedIndexStatus status = feedIndexMigrationService.readStatus();

      // then
      assertThat(status.lastReindexAt())
          .isEqualTo(endTime.atZone(ZoneId.systemDefault()).toInstant());
    }
  }
}
