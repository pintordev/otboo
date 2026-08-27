package com.sprint.mission.otboo.batch.feedmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexMigrationFailedException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.util.Map;
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
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
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

  @BeforeEach
  void setUp() {
    feedIndexMigrationService = new FeedIndexMigrationService(
        jobOperator, elasticsearchOperations, feedIndexMigrationJob);
  }

  private void givenAliasPointsToCurrentIndex() {
    given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
    given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
        .willReturn(Map.of(CURRENT_INDEX.getIndexName(), Set.of()));
  }

  private void givenNewIndexCreated() {
    given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
    given(entityOperations.createSettings()).willReturn(new Settings());
    given(entityOperations.createMapping()).willReturn(Document.create());
    given(elasticsearchOperations.indexOps(eq(NEW_INDEX))).willReturn(newIndexOperations);
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
      verify(elasticsearchOperations).indexOps(eq(NEW_INDEX));
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
    @DisplayName("Job이 정상 종료되지 않으면 alias를 전환하지 않는다")
    void Job이_정상_종료되지_않으면_alias를_전환하지_않는다() throws Exception {
      // given
      givenAliasPointsToCurrentIndex();
      givenNewIndexCreated();
      given(jobOperator.start(any(Job.class), any(JobParameters.class))).willReturn(jobExecution);
      given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);

      // when & then
      assertThatThrownBy(() -> feedIndexMigrationService.migrate())
          .isInstanceOf(FeedIndexMigrationFailedException.class);
      verify(aliasOperations, never()).alias(any(AliasActions.class));
    }

    @Test
    @DisplayName("한 세대를 남기고 오래된 인덱스를 삭제한다")
    void 한_세대를_남기고_오래된_인덱스를_삭제한다() throws Exception {
      // given
      given(elasticsearchOperations.indexOps(eq(ALIAS))).willReturn(aliasOperations);
      given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
          .willReturn(Map.of("feeds_v3", Set.of()));
      given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
      given(entityOperations.createSettings()).willReturn(new Settings());
      given(entityOperations.createMapping()).willReturn(Document.create());
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v4"))))
          .willReturn(newIndexOperations);
      given(elasticsearchOperations.indexOps(eq(IndexCoordinates.of("feeds_v2"))))
          .willReturn(obsoleteIndexOperations);
      given(obsoleteIndexOperations.exists()).willReturn(true);
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

      // when
      feedIndexMigrationService.migrate();

      // then
      InOrder inOrder = inOrder(newIndexOperations);
      inOrder.verify(newIndexOperations).delete();
      inOrder.verify(newIndexOperations).create(any(Settings.class), any(Document.class));
    }
  }
}
