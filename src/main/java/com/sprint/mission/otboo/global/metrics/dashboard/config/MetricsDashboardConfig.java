package com.sprint.mission.otboo.global.metrics.dashboard.config;

import com.sprint.mission.otboo.global.metrics.dashboard.CloudWatchEmfMeterRegistry;
import com.sprint.mission.otboo.global.metrics.dashboard.filter.MetricsDashboardWhitelist;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(MetricsDashboardProperties.class)
public class MetricsDashboardConfig {

  private static final Duration STEP = Duration.ofSeconds(60);

  @Bean
  public MetricsDashboardWhitelist metricsDashboardWhitelist(MetricsDashboardProperties properties) {
    return new MetricsDashboardWhitelist(properties.whitelistPrefixes());
  }

  @Bean
  public CloudWatchEmfMeterRegistry cloudWatchEmfMeterRegistry(
      MetricsDashboardProperties properties,
      MetricsDashboardWhitelist whitelist,
      JsonMapper jsonMapper
  ) {
    StepRegistryConfig config = new StepRegistryConfig() {
      @Override
      public String prefix() {
        return "metrics.dashboard";
      }

      @Override
      public String get(String key) {
        return null;
      }

      @Override
      public Duration step() {
        return STEP;
      }
    };
    return new CloudWatchEmfMeterRegistry(
        config, Clock.SYSTEM, properties.namespace(), whitelist, jsonMapper);
  }
}