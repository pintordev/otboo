package com.sprint.mission.otboo.batch.feedreindex.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.batch.feedreindex.dto.FeedReindexResult;
import com.sprint.mission.otboo.batch.feedreindex.service.FeedReindexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedReindexEndpoint")
class FeedReindexEndpointTest {

  @InjectMocks
  FeedReindexEndpoint endpoint;

  @Mock
  FeedReindexService feedReindexService;

  @Nested
  @DisplayName("전체 재색인")
  class ReindexAll {

    @Test
    @DisplayName("실행 결과를 그대로 반환한다")
    void 실행_결과를_그대로_반환한다() {
      // given
      FeedReindexResult expected = new FeedReindexResult(26L, 26L, 340L);
      given(feedReindexService.executeReindexAll()).willReturn(expected);

      // when
      FeedReindexResult actual = endpoint.reindexAll();

      // then
      assertThat(actual).isEqualTo(expected);
    }
  }
}
