package com.sprint.mission.otboo.batch.feedmigration.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexMigrationResult;
import com.sprint.mission.otboo.batch.feedmigration.dto.FeedIndexStatus;
import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexMigrationAlreadyRunningException;
import com.sprint.mission.otboo.batch.feedmigration.service.FeedIndexMigrationService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedIndexMigrationEndpoint")
class FeedIndexMigrationEndpointTest {

  @InjectMocks
  FeedIndexMigrationEndpoint endpoint;

  @Mock
  FeedIndexMigrationService feedIndexMigrationService;

  @Nested
  @DisplayName("마이그레이션 실행")
  class Migrate {

    @Test
    @DisplayName("실행 결과를 그대로 반환한다")
    void 실행_결과를_그대로_반환한다() {
      // given
      FeedIndexMigrationResult expected =
          new FeedIndexMigrationResult("feeds_v1", "feeds_v2", 26L, 218L);
      given(feedIndexMigrationService.migrate()).willReturn(expected);

      // when
      FeedIndexMigrationResult actual = endpoint.migrate();

      // then
      assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("이미 진행 중이면 409를 던진다")
    void 이미_진행_중이면_409를_던진다() {
      // given
      given(feedIndexMigrationService.migrate()).willReturn(null);

      // when & then
      assertThatThrownBy(() -> endpoint.migrate())
          .isInstanceOf(FeedIndexMigrationAlreadyRunningException.class)
          .extracting("status")
          .isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("상태 조회")
  class Status {

    @Test
    @DisplayName("서비스가 반환한 상태를 그대로 반환한다")
    void 서비스가_반환한_상태를_그대로_반환한다() {
      // given
      FeedIndexStatus expected = new FeedIndexStatus(
          "feeds", "feeds_v1", true, Set.of("searchText"), 26L, 26L,
          Instant.parse("2026-08-28T05:00:00Z"), Instant.parse("2026-08-28T03:00:00Z"));
      given(feedIndexMigrationService.readStatus()).willReturn(expected);

      // when
      FeedIndexStatus actual = endpoint.status();

      // then
      assertThat(actual).isEqualTo(expected);
    }
  }
}
