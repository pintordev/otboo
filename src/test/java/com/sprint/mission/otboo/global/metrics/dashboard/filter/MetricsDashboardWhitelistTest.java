package com.sprint.mission.otboo.global.metrics.dashboard.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MetricsDashboardWhitelist")
class MetricsDashboardWhitelistTest {

  MetricsDashboardWhitelist whitelist;

  @BeforeEach
  void setUp() {
    whitelist = new MetricsDashboardWhitelist(List.of("batch.", "recommendation.cache."));
  }

  @Nested
  @DisplayName("허용 여부 판정")
  class Matches {

    @Test
    @DisplayName("설정된 프리픽스로 시작하면 허용한다")
    void 설정된_프리픽스로_시작하면_허용한다() {
      // given
      String batchMeterName = "batch.weather-fetch.job.completed";
      String recommendationMeterName = "recommendation.cache.hit";

      // when
      boolean batchMatches = whitelist.matches(batchMeterName);
      boolean recommendationMatches = whitelist.matches(recommendationMeterName);

      // then
      assertThat(batchMatches).isTrue();
      assertThat(recommendationMatches).isTrue();
    }

    @Test
    @DisplayName("설정된 프리픽스가 아니면 거부한다")
    void 설정된_프리픽스가_아니면_거부한다() {
      // given
      String meterName = "jvm.memory.used";

      // when
      boolean matches = whitelist.matches(meterName);

      // then
      assertThat(matches).isFalse();
    }
  }
}