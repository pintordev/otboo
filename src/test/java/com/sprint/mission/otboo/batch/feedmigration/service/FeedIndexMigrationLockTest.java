package com.sprint.mission.otboo.batch.feedmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexMigrationResult;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.config.SchedulerLockConfig;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 마이그레이션이 동시에 실행되면 두 요청이 같은 대상 인덱스를 계산한다. 한쪽이 alias를 전환한 뒤 다른 쪽이 그 인덱스를 지울 수 있으므로 락으로 직렬화한다.
 */
@SpringBootTest(classes = {
    DataRedisAutoConfiguration.class, SchedulerLockConfig.class, FeedIndexMigrationService.class
})
class FeedIndexMigrationLockTest implements RedisTestContainerSupport {

  // 스프링 빈이 아니라 elasticsearchOperations·jobOperator가 돌려주는 값이므로 일반 mock으로 둔다.
  private final JobExecution jobExecution = mock(JobExecution.class);
  private final IndexOperations aliasOperations = mock(IndexOperations.class);
  private final IndexOperations entityOperations = mock(IndexOperations.class);
  private final IndexOperations newIndexOperations = mock(IndexOperations.class);

  @Autowired
  private FeedIndexMigrationService feedIndexMigrationService;

  @MockitoBean
  private JobOperator jobOperator;

  @MockitoBean
  private ElasticsearchOperations elasticsearchOperations;

  @MockitoBean
  private FeedIndexInspector feedIndexInspector;

  @MockitoBean
  private FeedRepository feedRepository;

  @MockitoBean
  private JobRepository jobRepository;

  @MockitoBean(name = "feedIndexMigrationJob")
  private Job feedIndexMigrationJob;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @BeforeEach
  void setUp() throws Exception {
    reset(jobExecution, aliasOperations, entityOperations, newIndexOperations);
    given(elasticsearchOperations.indexOps(IndexCoordinates.of(FeedDocument.INDEX_NAME)))
        .willReturn(aliasOperations);
    given(aliasOperations.getAliases(FeedDocument.INDEX_NAME))
        .willReturn(Map.of("feeds_v1", Set.of()));
    given(aliasOperations.alias(any(AliasActions.class))).willReturn(true);
    given(elasticsearchOperations.indexOps(FeedDocument.class)).willReturn(entityOperations);
    given(entityOperations.createSettings()).willReturn(new Settings());
    given(entityOperations.createMapping()).willReturn(Document.create());
    given(elasticsearchOperations.indexOps(IndexCoordinates.of("feeds_v2")))
        .willReturn(newIndexOperations);
    given(newIndexOperations.create(any(Settings.class), any(Document.class))).willReturn(true);

    // 락이 유지되는 동안 다른 스레드가 진입을 시도하도록 재색인을 지연시킨다.
    given(jobOperator.start(any(Job.class), any(JobParameters.class)))
        .willAnswer(invocation -> {
          Thread.sleep(500);
          return jobExecution;
        });
    given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
  }

  @Nested
  @DisplayName("마이그레이션 락")
  class MigrationLock {

    @Test
    @DisplayName("동시에 호출하면 하나만 실행되고 나머지는 null을 반환한다")
    void 동시에_호출하면_하나만_실행되고_나머지는_null을_반환한다() throws Exception {
      // given
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
        // when
        List<Future<FeedIndexMigrationResult>> futures = IntStream.range(0, 2)
            .<Future<FeedIndexMigrationResult>>mapToObj(i -> executor.submit(() -> {
              ready.countDown();
              start.await();
              return feedIndexMigrationService.migrate();
            }))
            .toList();
        ready.await();
        start.countDown();

        List<FeedIndexMigrationResult> results = new ArrayList<>();
        for (Future<FeedIndexMigrationResult> future : futures) {
          results.add(future.get(15, TimeUnit.SECONDS));
        }

        // then
        assertThat(results.stream().filter(Objects::nonNull).toList()).hasSize(1);

        verify(jobOperator, times(1)).start(any(Job.class), any(JobParameters.class));
      } finally {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }
}
