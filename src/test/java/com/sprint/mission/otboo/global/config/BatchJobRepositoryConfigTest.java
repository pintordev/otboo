package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
@EmbeddedKafka(partitions = 1, topics = NotificationKafkaTopics.NOTIFICATION_REQUESTED)
class BatchJobRepositoryConfigTest extends IntegrationTestSupport {

  private static final int CONCURRENT_JOB_COUNT = 10;

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  private Job weatherFetchJob;

  @MockitoBean
  private KmaForecastFetcher kmaForecastFetcher;

  @Autowired
  private KafkaListenerEndpointRegistry registry;

  @BeforeEach
  void setUp() {
    // 컨슈머 그룹 리밸런스(파티션 할당)가 끝나기 전에 컨텍스트가 도는 걸 막는다
    MessageListenerContainer container = registry.getListenerContainer("notificationRequestedConsumer");
    ContainerTestUtils.waitForAssignment(container, 1);
    jobOperatorTestUtils.setJob(weatherFetchJob);
  }

  @Nested
  @DisplayName("동시 Job 생성")
  class ConcurrentJobCreation {

    @Test
    @DisplayName("서로_다른_JobParameters로_동시에_시작해도_SERIALIZABLE_충돌_없이_모두_실행된다")
    void 서로_다른_JobParameters로_동시에_시작해도_SERIALIZABLE_충돌_없이_모두_실행된다() throws Exception {
      // given
      CountDownLatch ready = new CountDownLatch(CONCURRENT_JOB_COUNT);
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_JOB_COUNT);
      List<Throwable> failures = new CopyOnWriteArrayList<>();
      List<JobExecution> executions = new CopyOnWriteArrayList<>();

      try {
        // when - 서로 다른 JobParameters(UUID 포함)로 CONCURRENT_JOB_COUNT개 Job을 동시에 시작한다.
        // getUniqueJobParameters()는 SecureRandom.nextLong() 하나뿐이라 동시 호출 간 고유성을
        // 보장하지 않는다 - UUID를 직접 넣어 JobInstance 충돌 가능성을 없앤다
        List<Future<Object>> futures = IntStream.range(0, CONCURRENT_JOB_COUNT)
            .<Future<Object>>mapToObj(i -> executor.submit(() -> {
              ready.countDown();
              try {
                start.await();
                JobParametersBuilder jobParameters = new JobParametersBuilder()
                    .addString("uuid", UUID.randomUUID().toString());
                executions.add(jobOperatorTestUtils.startJob(jobParameters.toJobParameters()));
              } catch (Throwable e) {
                failures.add(e);
              }
              return null;
            }))
            .toList();
        ready.await();
        start.countDown();
        for (Future<Object> future : futures) {
          future.get(30, TimeUnit.SECONDS);
        }
      } finally {
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
      }

      // then - jobOperator.start() 자체가 SERIALIZABLE 충돌(CannotAcquireLockException)로
      // 실패하면 안 된다
      assertThat(failures).isEmpty();
      assertThat(executions).hasSize(CONCURRENT_JOB_COUNT);
      assertThat(executions).allSatisfy(execution ->
          assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED));
    }
  }
}