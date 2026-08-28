package com.sprint.mission.otboo.global.metrics.dashboard;

import com.sprint.mission.otboo.global.metrics.dashboard.filter.MetricsDashboardWhitelist;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class CloudWatchEmfMeterRegistry extends StepMeterRegistry {

  private final String namespace;
  private final MetricsDashboardWhitelist whitelist;
  private final JsonMapper jsonMapper;

  public CloudWatchEmfMeterRegistry(
      StepRegistryConfig config,
      Clock clock,
      String namespace,
      MetricsDashboardWhitelist whitelist,
      JsonMapper jsonMapper
  ) {
    super(config, clock);
    this.namespace = namespace;
    this.whitelist = whitelist;
    this.jsonMapper = jsonMapper;
    start(new NamedThreadFactory("cloudwatch-emf-metrics-publisher"));
  }

  @Override
  protected void publish() {
    getMeters().stream()
        .filter(meter -> whitelist.matches(meter.getId().getName()))
        .forEach(this::writeEmfLogLine);
  }

  private void writeEmfLogLine(Meter meter) {
    if (meter instanceof Counter counter) {
      writeCounter(meter.getId(), counter);
    } else if (meter instanceof Timer timer) {
      writeTimer(meter.getId(), timer);
    }
  }

  private void writeCounter(Meter.Id id, Counter counter) {
    double count = counter.count();
    if (count > 0) {
      emit(id, "Count", count);
    }
  }

  private void writeTimer(Meter.Id id, Timer timer) {
    if (timer.count() > 0) {
      emit(id, "Milliseconds", timer.mean(TimeUnit.MILLISECONDS));
    }
  }

  private void emit(Meter.Id id, String unit, double value) {
    String name = id.getName();
    List<Tag> tags = id.getTags();
    List<String> dimensionNames = tags.stream().map(Tag::getKey).toList();

    Map<String, Object> metricDefinition = Map.of("Name", name, "Unit", unit);
    Map<String, Object> metricDirective = Map.of(
        "Namespace", namespace,
        "Dimensions", List.of(dimensionNames),
        "Metrics", List.of(metricDefinition)
    );
    Map<String, Object> aws = Map.of(
        "Timestamp", clock.wallTime(),
        "CloudWatchMetrics", List.of(metricDirective)
    );

    Map<String, Object> logEvent = new LinkedHashMap<>();
    logEvent.put("_aws", aws);
    for (Tag tag : tags) {
      logEvent.put(tag.getKey(), tag.getValue());
    }
    logEvent.put(name, value);

    log.info(jsonMapper.writeValueAsString(logEvent));
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.MILLISECONDS;
  }
}