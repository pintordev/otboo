package com.sprint.mission.otboo.batch.feedreindex.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexBulkException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;

@DisplayName("FeedReindexSkipPolicy")
class FeedReindexSkipPolicyTest {

  private final FeedReindexSkipPolicy policy =
      new FeedReindexSkipPolicy(new FeedReindexProperties(500, 10, Duration.ofHours(2)));

  @Nested
  @DisplayName("skip 판단")
  class ShouldSkip {

    @Test
    @DisplayName("연결 실패는 skip하지 않는다")
    void 연결_실패는_skip하지_않는다() {
      // given
      Throwable t = new DataAccessResourceFailureException("Connection refused");

      // when
      boolean result = policy.shouldSkip(t, 0);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("개별 문서 오류는 skip한다")
    void 개별_문서_오류는_skip한다() {
      // given
      Throwable t = new UncategorizedElasticsearchException("mapper_parsing_exception");

      // when
      boolean result = policy.shouldSkip(t, 0);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("skipLimit을 넘으면 skip하지 않는다")
    void skipLimit을_넘으면_skip하지_않는다() {
      // given
      Throwable t = new UncategorizedElasticsearchException("mapper_parsing_exception");

      // when
      boolean result = policy.shouldSkip(t, 10);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("DataAccessException이 아닌 예외는 skip하지 않는다")
    void DataAccessException이_아닌_예외는_skip하지_않는다() {
      // given
      Throwable t = new IllegalStateException("예상 밖");

      // when
      boolean result = policy.shouldSkip(t, 0);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("bulk 색인 실패는 skipLimit까지 skip한다")
    void bulk_색인_실패는_skipLimit까지_skip한다() {
      // given
      Throwable t = FeedReindexBulkException.of(1);

      // when & then
      assertThat(policy.shouldSkip(t, 0)).isTrue();
      assertThat(policy.shouldSkip(t, 10)).isFalse();
    }
  }
}
