package com.sprint.mission.otboo.batch.feedreindex;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.authuser.user.repository.UserRepository;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@SpringBatchTest
@DisplayName("FeedReindexJob")
class FeedReindexJobIntegrationTest extends IntegrationTestSupport {

  private static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  private final List<UUID> createdUserIds = new ArrayList<>();

  @Autowired
  private JobOperatorTestUtils jobOperatorTestUtils;

  @Autowired
  @Qualifier("feedReindexJob")
  private Job feedReindexJob;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FeedSearchRepository feedSearchRepository;

  @Autowired
  private ElasticsearchOperations operations;

  @BeforeEach
  void setUp() {
    cleanUp();
    jobOperatorTestUtils.setJob(feedReindexJob);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    feedRepository.deleteAll();
    userRepository.deleteAllById(createdUserIds);
    createdUserIds.clear();
    feedSearchRepository.deleteAll();
    operations.indexOps(FeedDocument.class).refresh();
  }

  private Feed saveFeed(String content) {
    User author = userRepository.save(
        User.create("작성자", UUID.randomUUID() + "@otboo.io", "password"));
    createdUserIds.add(author.getId());
    return feedRepository.save(
        Feed.create(author.getId(), UUID.randomUUID(), content, DUMMY_SNAPSHOT, List.of()));
  }

  @Nested
  @DisplayName("전체 재색인")
  class ReindexAll {

    @Test
    @DisplayName("인덱스에 없는 활성 피드를 모두 색인한다")
    void 인덱스에_없는_활성_피드를_모두_색인한다() throws Exception {
      // given
      saveFeed("첫 번째 피드");
      saveFeed("두 번째 피드");

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          new JobParametersBuilder()
              .addLong("time", Instant.now().toEpochMilli())
              .addString("targetIndex", FeedDocument.INDEX_NAME)
              .toJobParameters());
      operations.indexOps(FeedDocument.class).refresh();

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(feedSearchRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 색인하지 않는다")
    void 소프트_삭제된_피드는_색인하지_않는다() throws Exception {
      // given
      saveFeed("살아있는 피드");
      Feed deleted = saveFeed("삭제된 피드");
      deleted.delete();
      feedRepository.save(deleted);

      // when
      JobExecution execution = jobOperatorTestUtils.startJob(
          new JobParametersBuilder()
              .addLong("time", Instant.now().toEpochMilli())
              .addString("targetIndex", FeedDocument.INDEX_NAME)
              .toJobParameters());
      operations.indexOps(FeedDocument.class).refresh();

      // then
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(feedSearchRepository.count()).isEqualTo(1L);
    }
  }
}
