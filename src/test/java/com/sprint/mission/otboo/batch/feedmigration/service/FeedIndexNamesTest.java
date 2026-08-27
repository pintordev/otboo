package com.sprint.mission.otboo.batch.feedmigration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.batch.feedmigration.exception.FeedIndexNameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeedIndexNames")
class FeedIndexNamesTest {

  @Nested
  @DisplayName("다음 버전 이름 산출")
  class NextVersionOf {

    @Test
    @DisplayName("현재 인덱스 이름의 버전을 하나 올린 이름을 반환한다")
    void 현재_인덱스_이름의_버전을_하나_올린_이름을_반환한다() {
      // when & then
      assertThat(FeedIndexNames.nextVersionOf("feeds_v1")).isEqualTo("feeds_v2");
      assertThat(FeedIndexNames.nextVersionOf("feeds_v9")).isEqualTo("feeds_v10");
    }

    @Test
    @DisplayName("규칙에 맞지 않는 이름이면 예외를 던진다")
    void 규칙에_맞지_않는_이름이면_예외를_던진다() {
      // when & then
      assertThatThrownBy(() -> FeedIndexNames.nextVersionOf("feeds"))
          .isInstanceOf(FeedIndexNameException.class);
      assertThatThrownBy(() -> FeedIndexNames.nextVersionOf("feeds_v"))
          .isInstanceOf(FeedIndexNameException.class);
      assertThatThrownBy(() -> FeedIndexNames.nextVersionOf("other_v1"))
          .isInstanceOf(FeedIndexNameException.class);
    }
  }

  @Nested
  @DisplayName("정리 대상 이름 산출")
  class IndexToDelete {

    @Test
    @DisplayName("한 세대를 남기고 그 이전 인덱스 이름을 반환한다")
    void 한_세대를_남기고_그_이전_인덱스_이름을_반환한다() {
      // when & then
      assertThat(FeedIndexNames.indexToDelete("feeds_v3")).hasValue("feeds_v1");
      assertThat(FeedIndexNames.indexToDelete("feeds_v4")).hasValue("feeds_v2");
    }

    @Test
    @DisplayName("남길 세대가 없으면 비어 있다")
    void 남길_세대가_없으면_비어_있다() {
      // when & then
      assertThat(FeedIndexNames.indexToDelete("feeds_v1")).isEmpty();
      assertThat(FeedIndexNames.indexToDelete("feeds_v2")).isEmpty();
    }
  }
}
